package com.hrsthrt74.afdian.data.auth

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.HomeCache
import com.hrsthrt74.afdian.data.content.ProfileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 登录态三态：Loading 表示正在从 DataStore 读取 token，此期间不渲染任何界面。
 *
 * [LoggedIn.epoch] 是会话代次：登录、切换账号、登出都会递增。
 * 已登录界面以它为 key 重建，从而销毁旧的会话级 ViewModel（见 SessionStoreHost）。
 */
sealed interface AuthState {
    data object Loading : AuthState
    data class LoggedIn(val token: String, val epoch: Long = 0L) : AuthState
    data object LoggedOut : AuthState
}

/**
 * 账号切换阶段。
 *
 * 刻意不塞进 [AuthState]：遮罩需要跨越「预检旧 token」和「新会话加载」两个阶段，
 * 而登录态在预检期间根本不该变化（失败要留在原账号）。
 *
 * [from] 是切换的起点账号，仅用于遮罩上的「左移换脸」动画（原头像 → 新头像）。
 * 老用户记录缺失、或当前账号还不在列表里时会是 null，此时遮罩退化成只显示目标头像。
 */
sealed interface AccountSwitchPhase {
    /** 没有进行中的切换 */
    data object Idle : AccountSwitchPhase

    /** 遮罩已出现，正在解密并预检目标账号的 token；此刻仍是旧会话 */
    data class Preparing(
        val target: SavedAccountSummary,
        val from: SavedAccountSummary? = null,
    ) : AccountSwitchPhase

    /** 新 token 已提交，等待新会话首屏数据就绪 */
    data class Waiting(
        val target: SavedAccountSummary,
        val epoch: Long,
        val from: SavedAccountSummary? = null,
    ) : AccountSwitchPhase
}

/** 登录目的：普通登录，还是在已登录状态下「添加账号」 */
private enum class LoginPurpose { Normal, AddAccount }

class AuthSession(application: Application) : AndroidViewModel(application) {
    private val store = AuthTokenStore(application)
    // 资料/首页缓存与登录会话同生命周期：登出时由本类负责清理。
    // 放在这里而不是 MainViewModel，是因为后者会随会话作用域销毁、协程可能被取消。
    private val profileCache = ProfileCache(application)
    private val homeCache = HomeCache(application)
    // 多账号列表：token 在写入前加密，读出来只给 UI 摘要（不含 token）
    private val savedAccountStore = SavedAccountStore(application)
    private val contentService = AfadianContentService(store)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 验证码发送中：用于禁用「发送」按钮并显示进度
    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode: StateFlow<Boolean> = _isSendingCode.asStateFlow()

    // 重发倒计时（秒）；0 表示可以发送
    private val _codeCooldown = MutableStateFlow(0)
    val codeCooldown: StateFlow<Int> = _codeCooldown.asStateFlow()

    // 图形验证码图片（PNG 字节）。null 表示当前不需要人机校验
    private val _captchaBytes = MutableStateFlow<ByteArray?>(null)
    val captchaBytes: StateFlow<ByteArray?> = _captchaBytes.asStateFlow()

    // 最近一次提交登录的账号。登录成功后的首屏过渡层仍展示登录页，
    // 用它把账号回填进输入框，避免用户看到自己刚输入的内容被清空。
    // 只记账号，不记密码/验证码（那两项登录成功后就不再需要）。
    private val _lastAccount = MutableStateFlow("")
    val lastAccount: StateFlow<String> = _lastAccount.asStateFlow()

    // ── 多账号 ──
    private val _accounts = MutableStateFlow<List<SavedAccountSummary>>(emptyList())

    /** 已保存账号（最近使用倒序），面板直接用。 */
    val accounts: StateFlow<List<SavedAccountSummary>> = _accounts.asStateFlow()

    private val _switchPhase = MutableStateFlow<AccountSwitchPhase>(AccountSwitchPhase.Idle)

    /** 切换阶段；遮罩的显隐完全由它驱动。 */
    val switchPhase: StateFlow<AccountSwitchPhase> = _switchPhase.asStateFlow()

    private val _switchNotice = MutableStateFlow<String?>(null)

    /** 切换/删除失败的一次性提示，UI 弹完 Snackbar 调用 [consumeSwitchNotice]。 */
    val switchNotice: StateFlow<String?> = _switchNotice.asStateFlow()

    private val _invalidAccountIds = MutableStateFlow<Set<String>>(emptySet())

    /** 预检判定「登录已失效」的账号 id，面板上给它们打角标。 */
    val invalidAccountIds: StateFlow<Set<String>> = _invalidAccountIds.asStateFlow()

    private val _isAddingAccount = MutableStateFlow(false)

    /** 是否正在「添加账号」全屏登录浮层里。 */
    val isAddingAccount: StateFlow<Boolean> = _isAddingAccount.asStateFlow()

    // 倒计时协程：重复点击发送时先取消上一轮，避免出现两个计时器把数字跳乱
    private var cooldownJob: Job? = null
    // 登录协程：添加账号时取消要能中断请求
    private var loginJob: Job? = null
    private var switchJob: Job? = null
    private var switchTimeoutJob: Job? = null
    // 请求代次：取消/重入时让旧协程的收尾逻辑失效
    private var loginSeq = 0L
    private var switchSeq = 0L

    init {
        // 启动时异步读取已保存的 token；读完才把状态置为 LoggedIn/LoggedOut，
        // 期间 UI 保持 Loading，避免登录页闪烁。
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { store.token }
            // 账号列表独立于登录态：未登录时也要读出来，
            // 登录页才能给出「用已保存账号继续」的入口——退出当前账号只删掉当前那一条，
            // 其他账号的凭证仍在本地，冷启动后同样应该能一键切回去。
            savedAccountStore.load().let { records ->
                _accounts.value = records.map { it.toSummary() }
                if (!token.isNullOrBlank()) {
                    // 优先用账号列表里的指纹判定当前身份（不需要网络），
                    // 老用户还没有记录时退回资料缓存，保证首帧布局落在正确分区。
                    val listedId = resolveCurrentUserId(records, authTokenFingerprint(token))
                    val cachedId = if (listedId == null) {
                        withContext(Dispatchers.IO) { profileCache.load()?.user?.user_id }
                    } else null
                    (listedId ?: cachedId)?.let(CurrentAccount::update)
                    AppLog.d(
                        "AuthSession",
                        "冷启动账号身份：${if (listedId != null) "账号列表命中" else if (cachedId != null) "资料缓存回退" else "未知"}",
                    )
                }
            }
            // 冷启动登录态判定结果：只记录「有没有 token」，绝不打印 token 本身
            AppLog.d("AuthSession", "启动读取登录态完成：${if (token.isNullOrBlank()) "未登录" else "已登录"}")
            _state.value = if (token.isNullOrBlank()) {
                AuthState.LoggedOut
            } else {
                AuthState.LoggedIn(token, SessionEpoch.current())
            }
            // 迁移不阻塞首帧：老用户补建账号记录
            if (!token.isNullOrBlank()) launch { seedAccountIfMissing(token) }
        }
        // 身份晚到（弱网首启、首页刷新写入 CurrentAccount）时再补建一次
        viewModelScope.launch {
            CurrentAccount.userId.collect { userId ->
                if (userId != null) seedAccountIfMissing(store.token ?: return@collect)
            }
        }
    }

    /** 密码登录。 */
    fun login(account: String, password: String) {
        // 只记账号长度不记账号内容（手机号/邮箱属于敏感信息）
        startLogin("密码登录", account, LoginPurpose.Normal) { AuthService().passwordLogin(account, password) }
    }

    /**
     * 验证码登录。
     *
     * @param account 手机号或邮箱
     * @param code 收到的短信/邮件验证码
     */
    fun loginWithCode(account: String, code: String) {
        startLogin("验证码登录", account, LoginPurpose.Normal) { AuthService().codeLogin(account, code) }
    }

    /**
     * 网页登录：凭据已经在 WebView 里拿到了，直接提交。
     *
     * 与 [login] / [loginWithCode] 的区别只在于「token 从哪来」——那两种是本 App
     * 调接口换的，这一种是网页端登录成功后由 `CookieManager` 读出来的。
     * 拿到手之后的后半程（落盘 → 切登录态 → 预取身份 → 存账号列表）完全一致，
     * 所以直接复用 [startLogin]，不多写一套提交逻辑。
     *
     * 账号参数传空串：网页登录时用户没在本 App 的输入框里填过账号，
     * 首屏过渡层的账号回填保持为空是正确的。
     */
    fun loginWithToken(token: String) {
        startLogin("网页登录", "", LoginPurpose.Normal) { token }
    }

    /**
     * 「添加账号」浮层里的网页登录：成功后保存账号并切换过去。
     *
     * 与 [loginNewAccount] 的关系同 [loginWithToken] 与 [login] 的关系：
     * 只是 token 的来源不同，后续流程复用。
     */
    fun loginNewAccountWithToken(token: String) {
        startLogin("添加账号", "", LoginPurpose.AddAccount) { token }
    }

    /** 「添加账号」浮层里的密码登录：成功后保存账号并切换过去。 */
    fun loginNewAccount(account: String, password: String) {
        startLogin("添加账号", account, LoginPurpose.AddAccount) { AuthService().passwordLogin(account, password) }
    }

    /** 「添加账号」浮层里的验证码登录。 */
    fun loginNewAccountWithCode(account: String, code: String) {
        startLogin("添加账号", account, LoginPurpose.AddAccount) { AuthService().codeLogin(account, code) }
    }

    /**
     * 发送登录验证码。
     *
     * 首次调用 captchaCode 传空串；若服务端要求人机校验，会把图形验证码图片放进
     * [captchaBytes]，由界面展示后再调用一次并带上用户输入的图形码。
     */
    fun sendLoginCode(account: String, captchaCode: String) {
        // 已经在发送或还在倒计时里，直接忽略重复点击（按钮本身也会禁用，这里兜底）
        if (_isSendingCode.value || _codeCooldown.value > 0) return
        viewModelScope.launch {
            // 协程真正开始执行时再判一次，防止极短时间内两次点击都通过上面的同步检查
            if (_isSendingCode.value) return@launch
            _isSendingCode.value = true
            _error.value = null
            AppLog.d("AuthSession", "开始发送登录验证码 account_len=${account.length} captcha=${captchaCode.isNotBlank()}")
            try {
                val result = withContext(Dispatchers.IO) { AuthService().sendLoginCode(account, captchaCode) }
                when (result) {
                    SendCodeResult.Sent -> {
                        // 发码成功：收起图形验证码，开始 60 秒倒计时
                        _captchaBytes.value = null
                        AppLog.d("AuthSession", "登录验证码已发送")
                        startCooldown()
                    }

                    is SendCodeResult.CaptchaRequired -> {
                        // 需要人机校验：解码图片交给界面展示。
                        // 注意这里不算失败，所以不写 _error；只有「图形码填错了」才提示。
                        val imageBytes = withContext(Dispatchers.IO) {
                            decodeCaptchaDataUri(result.imageDataUri)
                        }
                        _captchaBytes.value = imageBytes
                        when {
                            // 服务端说要图形码却没给图，界面会一直停在「发不出去」的状态，必须明说
                            imageBytes == null -> _error.value = "图形验证码加载失败，请重试"
                            captchaCode.isNotBlank() -> _error.value = result.message ?: "图形验证码错误"
                        }
                        AppLog.d("AuthSession", "发码需要图形验证码，图片=${if (imageBytes == null) "缺失" else "已就绪"}")
                    }
                }
            } catch (e: Exception) {
                AppLog.e("AuthSession", "发送登录验证码失败: ${e.message}", e)
                _error.value = e.message ?: "验证码发送失败，请稍后重试"
            } finally {
                _isSendingCode.value = false
            }
        }
    }

    /**
     * 密码登录与验证码登录共用的收尾逻辑：拿 token → 按目的分流。
     *
     * @param label 仅用于日志区分登录方式
     * @param purpose 普通登录会直接进入主页；添加账号会先保存记录再切换
     */
    private fun startLogin(
        label: String,
        account: String,
        purpose: LoginPurpose,
        block: suspend () -> String?,
    ) {
        // 先记下账号：登录成功后首屏过渡层要靠它回填输入框
        _lastAccount.value = account.trim()
        loginJob?.cancel()
        val seq = ++loginSeq
        loginJob = viewModelScope.launch {
            _isLoggingIn.value = true
            _error.value = null
            // 只记账号长度不记账号内容（手机号/邮箱属于敏感信息）
            AppLog.d("AuthSession", "开始$label account_len=${account.length}")
            try {
                val token = withContext(Dispatchers.IO) { block() }
                if (token.isNullOrBlank()) throw IllegalStateException("登录失败，服务器未返回登录凭证")
                _captchaBytes.value = null
                when (purpose) {
                    LoginPurpose.Normal -> commitLogin(token)
                    LoginPurpose.AddAccount -> commitAddAccount(token)
                }
            } catch (e: Exception) {
                // 登录失败走 E 级：无论调试开关是否打开都能在 Logcat 看到
                AppLog.e("AuthSession", "$label 失败: ${e.message}", e)
                _error.value = e.message ?: "登录失败，请检查网络和账号信息"
            } finally {
                // 被新的登录请求取代时不要把新请求的「登录中」状态提前关掉
                if (seq == loginSeq) _isLoggingIn.value = false
            }
        }
    }

    /**
     * 普通登录成功：落盘 token → 切登录态 → 预取身份并存为账号记录。
     */
    private suspend fun commitLogin(token: String) {
        AppLog.d("AuthSession", "登录成功，凭证已保存")
        withContext(Dispatchers.IO) { store.writeToken(token) }
        _state.value = AuthState.LoggedIn(token, SessionEpoch.next())

        // 预取账号身份（与首页刷新并行）。
        // 不做这一步的话，从登录成功到首页刷新拿到资料之间 CurrentAccount 是空的，
        // 首页所有账号级设置会先读空分区的默认值渲染一帧，等身份绑定时再整页跳布局。
        val profile = runCatching {
            withContext(Dispatchers.IO) { contentService.fetchProfileWithToken(token) }
        }.onFailure {
            AppLog.w("AuthSession", "预取账号身份失败，等首页刷新兜底", it)
        }.getOrNull()

        val user = profile?.user
        if (user != null && user.user_id.isNotBlank()) {
            CurrentAccount.update(user.user_id)
            withContext(Dispatchers.IO) {
                savedAccountStore.upsert(
                    userId = user.user_id,
                    name = user.name,
                    avatar = user.avatar,
                    token = token,
                    now = System.currentTimeMillis(),
                )
            }
            refreshAccountListInternal()
            AppLog.d("AuthSession", "已预取账号身份并记入账号列表")
        }
    }

    /**
     * 添加账号成功：先验证身份、存入账号列表，再走与切换完全相同的会话提交。
     *
     * 关键不变量：这里的 token 只存在于本次协程的局部变量里，成功前绝不写 `auth_token`。
     */
    private suspend fun commitAddAccount(token: String) {
        val profile = withContext(Dispatchers.IO) { contentService.fetchProfileWithToken(token) }
        val user = profile.user
        if (user.user_id.isBlank()) error("无法确认账号身份")

        val saved = withContext(Dispatchers.IO) {
            savedAccountStore.upsert(
                userId = user.user_id,
                name = user.name,
                avatar = user.avatar,
                token = token,
                now = System.currentTimeMillis(),
            )
        }
        if (!saved) error("最多只能保存 $MAX_SAVED_ACCOUNTS 个账号，请先删除一个")

        refreshAccountListInternal()
        // 先关登录浮层，再显示切换遮罩，避免两层叠在一起
        _isAddingAccount.value = false
        AppLog.d("AuthSession", "账号已添加，开始切换会话")

        // 新 token 的指纹与旧缓存不同，必须重建会话（缓存会在 commitSession 里清掉）
        val summary = SavedAccountSummary(
            userId = user.user_id,
            name = user.name,
            avatar = user.avatar,
            lastUsedAt = System.currentTimeMillis(),
        )
        // 添加账号同样是一次切换：起点是当前仍登录着的账号
        runCatching { commitSession(token, user.user_id, summary, currentAccountSummary()) }.onFailure {
            _switchPhase.value = AccountSwitchPhase.Idle
            _switchNotice.value = "切换账号失败：${it.message ?: "未知错误"}"
            AppLog.w("AuthSession", "添加账号后切换失败", it)
        }
    }

    // ── 账号切换 ──

    /**
     * 当前账号的摘要，即「切换起点」。
     *
     * 一次切换内只算一次并向下传递：`commitSession` 会更新 [CurrentAccount]，
     * 中途重算可能已经拿到新身份，导致遮罩上的原账号头像变成目标账号。
     */
    private fun currentAccountSummary(): SavedAccountSummary? {
        val currentId = CurrentAccount.userId.value ?: return null
        return _accounts.value.firstOrNull { it.userId == currentId }
    }

    /**
     * 切换到已保存的另一个账号。
     *
     * 流程：显示遮罩 → 解密并预检 token → 成功则提交新会话 → 等首屏就绪解除遮罩。
     * 预检失败**不修改任何登录状态**，用户留在原账号。
     */
    fun switchAccount(userId: String) {
        if (_switchPhase.value !is AccountSwitchPhase.Idle) return
        if (_isAddingAccount.value) return
        if (userId == CurrentAccount.userId.value) return
        val target = _accounts.value.firstOrNull { it.userId == userId } ?: return
        // 切换起点在整条链路上保持不变（见 currentAccountSummary 的说明）
        val from = currentAccountSummary()

        val seq = ++switchSeq
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            _switchPhase.value = AccountSwitchPhase.Preparing(target, from)
            AppLog.d("AuthSession", "开始切换账号")

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val token = savedAccountStore.readToken(userId)
                        ?: error("账号凭证已损坏，请重新登录该账号")
                    // 用目标 token 预检：能拿到资料才说明这份凭证还有效
                    token to contentService.fetchProfileWithToken(token)
                }
            }
            if (seq != switchSeq) return@launch

            result.onFailure { error ->
                val failure = classifySwitchFailure(error)
                _switchPhase.value = AccountSwitchPhase.Idle
                if (failure == SwitchFailure.Expired) {
                    _invalidAccountIds.value = _invalidAccountIds.value + userId
                    _switchNotice.value = "「${target.name.ifBlank { "该账号" }}」登录状态已失效，请重新登录"
                } else {
                    _switchNotice.value = "网络异常，已取消切换"
                }
                AppLog.w("AuthSession", "切换账号失败（${failure.name}）", error)
            }.onSuccess { pair ->
                val (token, profile) = pair
                // 指纹能对上但服务端返回了别的账号：数据不一致，宁可取消
                if (profile.user.user_id != userId) {
                    _switchPhase.value = AccountSwitchPhase.Idle
                    _switchNotice.value = "账号信息不匹配，已取消切换"
                    AppLog.w("AuthSession", "预检返回的 user_id 与本地记录不一致")
                    return@onSuccess
                }
                commitSession(token, userId, target, from)
            }
        }
    }

    /**
     * 提交新会话：清缓存 → 换 token → 更新身份 → 递增代次 → 切换登录态。
     *
     * 顺序不能变：`ProfileCache` 的归属由 token 指纹判定，必须在新 token 落盘前清掉，
     * 否则新会话会读到上一个账号的资料。
     */
    private suspend fun commitSession(
        token: String,
        userId: String,
        target: SavedAccountSummary,
        from: SavedAccountSummary?,
    ) {
        val epoch = SessionEpoch.next()
        // 先置 Waiting 再换 state：中间任何一帧都不能让遮罩消失
        _switchPhase.value = AccountSwitchPhase.Waiting(target, epoch, from)

        withContext(Dispatchers.IO) {
            profileCache.clear()
            homeCache.clear()
            store.writeToken(token)
            savedAccountStore.touch(userId, System.currentTimeMillis())
        }
        CurrentAccount.update(userId)
        // 预检通过说明凭证有效，清掉历史遗留的失效角标
        if (userId in _invalidAccountIds.value) {
            _invalidAccountIds.value = _invalidAccountIds.value - userId
        }
        refreshAccountListInternal()
        _state.value = AuthState.LoggedIn(token, epoch)
        startSwitchTimeout(epoch)
    }

    /** 新会话首屏就绪（由 MainActivity 在 canShowMain 变 true 时调用）。 */
    fun notifyFirstScreenReady(epoch: Long) {
        val phase = _switchPhase.value
        if (phase is AccountSwitchPhase.Waiting && phase.epoch == epoch) {
            switchTimeoutJob?.cancel()
            _switchPhase.value = AccountSwitchPhase.Idle
            AppLog.d("AuthSession", "新账号首屏已就绪，解除切换遮罩")
        }
    }

    /** 首屏迟迟不就绪时的兜底：不能让用户永远停在遮罩上。 */
    private fun startSwitchTimeout(epoch: Long) {
        switchTimeoutJob?.cancel()
        switchTimeoutJob = viewModelScope.launch {
            delay(SWITCH_OVERLAY_TIMEOUT_MS.milliseconds)
            val phase = _switchPhase.value
            if (phase is AccountSwitchPhase.Waiting && phase.epoch == epoch) {
                _switchPhase.value = AccountSwitchPhase.Idle
                AppLog.w("AuthSession", "等待首屏超时，强制解除切换遮罩")
            }
        }
    }

    // ── 删除账号 ──

    /**
     * 删除一个已保存账号。
     *
     * - 非当前账号：直接删记录。
     * - 当前账号：先按最近使用顺序预检候选，成功后才删当前记录并切过去；
     *   候选全部失效则删记录并登出。网络异常一律中止删除，保留原账号。
     */
    fun deleteAccount(userId: String) {
        if (_switchPhase.value !is AccountSwitchPhase.Idle) return
        if (_isAddingAccount.value) return
        viewModelScope.launch {
            val currentId = CurrentAccount.userId.value
            if (userId != currentId) {
                withContext(Dispatchers.IO) { savedAccountStore.remove(userId) }
                refreshAccountListInternal()
                _switchNotice.value = "已删除该账号"
                return@launch
            }

            val records = withContext(Dispatchers.IO) { savedAccountStore.load() }
            val candidates = orderSwitchCandidates(records, userId)
            if (candidates.isEmpty()) {
                withContext(Dispatchers.IO) { savedAccountStore.remove(userId) }
                performLogout()
                return@launch
            }
            // 切换起点：被删的就是当前账号，所以 from 就是它自己
            val from = currentAccountSummary()

            for (candidate in candidates) {
                val target = candidate.toSummary()
                _switchPhase.value = AccountSwitchPhase.Preparing(target, from)
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val token = savedAccountStore.readToken(candidate.userId)
                            ?: error("账号凭证已损坏")
                        token to contentService.fetchProfileWithToken(token)
                    }
                }
                val pair = result.getOrNull()
                if (pair != null) {
                    withContext(Dispatchers.IO) { savedAccountStore.remove(userId) }
                    commitSession(pair.first, candidate.userId, target, from)
                    return@launch
                }
                val failure = classifySwitchFailure(result.exceptionOrNull() ?: RuntimeException("未知错误"))
                if (failure == SwitchFailure.Network) {
                    // 网络异常：中止删除，原账号与记录都不动
                    _switchPhase.value = AccountSwitchPhase.Idle
                    _switchNotice.value = "网络异常，已取消删除"
                    AppLog.w("AuthSession", "删除当前账号时网络异常，中止")
                    return@launch
                }
                _invalidAccountIds.value = _invalidAccountIds.value + candidate.userId
                AppLog.w("AuthSession", "兜底账号凭证失效，尝试下一个")
            }

            // 所有候选都失效：删掉当前记录并回到登录页
            withContext(Dispatchers.IO) { savedAccountStore.remove(userId) }
            performLogout()
        }
    }

    // ── 添加账号 ──

    /** 打开「添加账号」全屏登录浮层。 */
    fun beginAddAccount() {
        if (_switchPhase.value !is AccountSwitchPhase.Idle) return
        if (_isAddingAccount.value) return
        clearLoginTransientState()
        _isAddingAccount.value = true
        AppLog.d("AuthSession", "进入添加账号流程")
    }

    /** 关闭「添加账号」浮层；登录请求会被取消，临时 token 从未落盘。 */
    fun cancelAddAccount() {
        if (!_isAddingAccount.value) return
        loginJob?.cancel()
        clearLoginTransientState()
        _isAddingAccount.value = false
        AppLog.d("AuthSession", "已取消添加账号")
    }

    // ── 账号列表 ──

    /** 重新读一遍账号列表；打开面板前调用，顺带让排序反映最近使用。 */
    fun refreshAccountList() {
        viewModelScope.launch { refreshAccountListInternal() }
    }

    /** 消费一次性切换提示（Snackbar 弹出后调用）。 */
    fun consumeSwitchNotice() {
        _switchNotice.value = null
    }

    private suspend fun refreshAccountListInternal() {
        val records = withContext(Dispatchers.IO) { savedAccountStore.load() }
        _accounts.value = records.map { it.toSummary() }
        // 已删除的账号不该继续留着「登录已失效」角标
        if (_invalidAccountIds.value.isNotEmpty()) {
            val aliveIds = records.mapTo(HashSet()) { it.userId }
            _invalidAccountIds.value = _invalidAccountIds.value intersect aliveIds
        }
    }

    /**
     * 老用户迁移：账号列表里没有当前活动 token 对应记录时补建一条。
     *
     * 三种来源依次尝试：资料缓存（零网络）→ 用活动 token 拉一次资料 → 等首页刷新完成后再来一次。
     * 幂等：记录已存在时只用最新资料校正昵称/头像，不重复写入。
     */
    private suspend fun seedAccountIfMissing(activeToken: String) {
        if (activeToken.isBlank()) return
        val fingerprint = authTokenFingerprint(activeToken)
        val records = withContext(Dispatchers.IO) { savedAccountStore.load() }

        val existing = records.firstOrNull { it.tokenFingerprint == fingerprint }
        if (existing != null) {
            profileCache.load()?.user?.let { user ->
                if (user.name != existing.name || user.avatar.orEmpty() != existing.avatar) {
                    withContext(Dispatchers.IO) {
                        savedAccountStore.updateProfile(user.user_id, user.name, user.avatar)
                    }
                }
            }
            refreshAccountListInternal()
            return
        }

        val user = profileCache.load()?.user
            ?: runCatching { withContext(Dispatchers.IO) { contentService.fetchProfileWithToken(activeToken).user } }
                .getOrNull()
        if (user != null && user.user_id.isNotBlank()) {
            withContext(Dispatchers.IO) {
                savedAccountStore.upsert(
                    userId = user.user_id,
                    name = user.name,
                    avatar = user.avatar,
                    token = activeToken,
                    now = System.currentTimeMillis(),
                )
            }
        }
        refreshAccountListInternal()
    }

    /** 60 秒重发倒计时；页面退出不会中断，但协程随 ViewModel 销毁而取消。 */
    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in CODE_COOLDOWN_SECONDS downTo 1) {
                _codeCooldown.value = remaining
                delay(1.seconds)
            }
            _codeCooldown.value = 0
        }
    }

    /** 清空登录错误提示（切换登录方式时调用，避免红字残留）。 */
    fun clearError() { _error.value = null }

    /**
     * 退出当前账号：清凭证与缓存，**保留其他已保存账号**（下次登录可一键切回）。
     *
     * 「保留其他账号」不包括当前这个：手动退出的账号要从账号列表里删掉，
     * 之后必须重新输入密码，不能被登录页的「已保存账号」卡片一键切回。
     */
    fun logout() {
        viewModelScope.launch {
            // 活动 token 必须在 store.clear() 之前读：退出登录要靠它的指纹定位账号记录。
            // 也不能拿 CurrentAccount 当依据：MainViewModel.onLoggedOut() 会在调用本方法之前
            // 先把它置空，那样一条记录都删不掉，手动退出的账号还能被一键切回去。
            val activeToken = withContext(Dispatchers.IO) { store.token }
            val currentId = CurrentAccount.userId.value
            withContext(Dispatchers.IO) {
                store.clear()
                // 首页快照与资料缓存和 token 同生命周期，必须一起清：
                // 否则换账号后冷启动会先回填上一个账号的数据。
                profileCache.clear()
                homeCache.clear()
                // 账号身份一并清空：浏览历史/搜索历史会立刻切回空分区
                CurrentAccount.update(null)
            }
            // 账号记录单独删一步：它依赖 clear() 之前读到的 token
            removeLoggedOutAccount(activeToken, currentId)
            switchJob?.cancel()
            switchTimeoutJob?.cancel()
            _switchPhase.value = AccountSwitchPhase.Idle
            _isAddingAccount.value = false
            // 登出代次 +1：旧会话所有在途结果作废
            SessionEpoch.next()
            clearLoginTransientState()
            refreshAccountListInternal()
            // 登录态附属状态复位，避免下次打开登录页还残留倒计时或旧图形码
            AppLog.d("AuthSession", "已退出当前账号，本地凭证与缓存已清除（其他账号保留）")
            _state.value = AuthState.LoggedOut
        }
    }

    /**
     * 删除「刚退出登录的那条」账号记录。
     *
     * 以活动 token 的指纹为准：这是唯一能可靠指出「这条记录就是刚才那个会话」的依据，
     * 且不依赖任何内存态。指纹匹配不到（老记录没存指纹、或记录已被删）时退回 userId，
     * 两者都找不到就只留一条日志——那说明列表本来就没有这条记录。
     *
     * @param activeToken 清理前读到的活动 token
     * @param fallbackUserId CurrentAccount 里的 userId，可能已被上层提前置空
     */
    private suspend fun removeLoggedOutAccount(activeToken: String?, fallbackUserId: String?) {
        val fingerprint = activeToken?.takeIf { it.isNotBlank() }?.let(::authTokenFingerprint)
        val records = withContext(Dispatchers.IO) { savedAccountStore.load() }
        val target = pickLoggedOutAccount(records, fingerprint, fallbackUserId)
        if (target == null) {
            AppLog.d("AuthSession", "退出登录后没有匹配到账号记录，账号列表无需删除")
            return
        }
        withContext(Dispatchers.IO) { savedAccountStore.remove(target.userId) }
        AppLog.d("AuthSession", "已移除退出登录的账号记录，重新登录需要输入密码")
    }

    private fun clearLoginTransientState() {
        cooldownJob?.cancel()
        _codeCooldown.value = 0
        _captchaBytes.value = null
        _error.value = null
    }

    /** 登出收尾（供删除当前账号且无兜底时复用，已在协程上下文中）。 */
    private suspend fun performLogout() {
        val currentId = CurrentAccount.userId.value
        withContext(Dispatchers.IO) {
            store.clear()
            profileCache.clear()
            homeCache.clear()
            currentId?.let { savedAccountStore.remove(it) }
            CurrentAccount.update(null)
        }
        SessionEpoch.next()
        clearLoginTransientState()
        switchTimeoutJob?.cancel()
        _switchPhase.value = AccountSwitchPhase.Idle
        refreshAccountListInternal()
        AppLog.d("AuthSession", "无可切换账号，已退出登录")
        _state.value = AuthState.LoggedOut
    }

    private companion object {
        /** 重发验证码的冷却秒数；与网页端一致，避免短时间内反复请求被服务端限流 */
        const val CODE_COOLDOWN_SECONDS = 60

        /**
         * 切换遮罩的兜底时长。
         *
         * 必须比首页自身的首屏门控（8s）长：正常路径一定是「首屏就绪」先解除，
         * 只有真的卡住时才会走到这里。
         */
        const val SWITCH_OVERLAY_TIMEOUT_MS = 12_000L
    }
}

/**
 * 把 `data:image/png;base64,xxxx` 形式的图形验证码拆成 PNG 字节。
 *
 * 单独抽成顶层函数便于单元测试：只取第一个逗号之后的内容，
 * 没有 `data:` 前缀时按纯 base64 处理。用 [java.util.Base64] 而不是
 * `android.util.Base64`，这样纯 JVM 单测里也能直接跑。
 *
 * @return 解码后的字节；入参为空或 base64 非法时返回 null
 */
internal fun decodeCaptchaDataUri(dataUri: String?): ByteArray? {
    val base64 = dataUri
        // 有逗号取逗号之后（data URI），没有逗号就当作纯 base64
        ?.let { if (it.contains(',')) it.substringAfter(',') else it }
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return runCatching { java.util.Base64.getDecoder().decode(base64) }.getOrNull()
}

/** 基于 DataStore 的加密 token 存取，替代旧的 SharedPreferences("auth")。 */
class AuthTokenStore(private val application: Application) {
    // 加解密统一走 TokenCipher：多账号的每份 token 用同一把 KeyStore 密钥加密
    private val cipher = TokenCipher()

    /** 同步读取（内部可能阻塞一次磁盘 IO，请勿在主线程首次调用） */
    val token: String?
        get() {
            if (!sharedLoaded) load()
            return sharedCachedToken
        }

    private fun load() {
        synchronized(cacheLock) {
            if (sharedLoaded) return
            sharedCachedToken = runBlocking {
                application.authDataStore.data.first()[TOKEN_KEY]?.let(cipher::decrypt)
            }
            sharedLoaded = true
        }
    }

    suspend fun writeToken(value: String) {
        val encrypted = cipher.encrypt(value)
        application.authDataStore.edit { it[TOKEN_KEY] = encrypted }
        synchronized(cacheLock) {
            sharedCachedToken = value
            sharedLoaded = true
        }
    }

    suspend fun clear() {
        application.authDataStore.edit { it.remove(TOKEN_KEY) }
        synchronized(cacheLock) {
            sharedCachedToken = null
            sharedLoaded = true
        }
    }

    // 与旧 SP 中的键名一致
    private val TOKEN_KEY = stringPreferencesKey("auth_token")

    private companion object {
        // 同一进程内所有 AuthTokenStore 实例共享镜像，避免不同 ViewModel 看到过期登录态。
        private val cacheLock = Any()
        @Volatile private var sharedCachedToken: String? = null
        @Volatile private var sharedLoaded = false
    }
}

/**
 * token 指纹：SHA-256 前 16 位十六进制。
 *
 * 用来判断「某个账号记录是不是当前活动账号」：活动 token 的指纹与账号记录里的指纹
 * 一致即为当前账号。哈希不可逆，落盘也不泄露 token 本身。
 */
fun authTokenFingerprint(token: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(16)

/**
 * 当前登录会话的指纹：token 的 SHA-256 前 16 位十六进制。
 *
 * 用来给「与账号绑定」的本地缓存打标：换账号后 token 变化，指纹随之变化，
 * 旧缓存据此自动失效，不必依赖登出清理一定成功（例如登出途中被杀进程）。
 *
 * @return 未登录（没有 token）时返回 null
 */
fun authSessionFingerprint(application: Application): String? =
    AuthTokenStore(application).token?.takeIf { it.isNotBlank() }?.let(::authTokenFingerprint)

/** 进程级单例的认证 DataStore */
private val Application.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")
