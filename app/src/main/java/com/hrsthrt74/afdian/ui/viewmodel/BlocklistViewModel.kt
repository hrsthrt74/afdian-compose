package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.BlackedItem
import com.hrsthrt74.afdian.data.content.BlackedType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 单个名单 tab 的完整 UI 状态：数据、分页与加载/错误显式建模
 * （DESIGN.md 约定：不要用一个 Boolean 含糊表达）。
 */
data class BlackedListUi(
    val items: List<BlackedItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

/**
 * 屏蔽 / 拒绝名单页的状态。
 *
 * 两个 tab（屏蔽 block / 拒绝 reject）对应同一条接口的不同 type 参数，
 * 状态各自独立：进入页面时并行加载两个名单，切换 tab 不重新请求，
 * 任一 tab 加载失败也不影响另一个。
 */
class BlocklistViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))

    // 当前 tab：0 = 屏蔽列表，1 = 拒绝列表
    private val _kind = MutableStateFlow(0)
    val kind = _kind.asStateFlow()

    // 屏蔽 / 拒绝两个 tab 的状态各自独立，切换与失败互不影响
    private val _blockUi = MutableStateFlow(BlackedListUi())
    val blockUi = _blockUi.asStateFlow()
    private val _rejectUi = MutableStateFlow(BlackedListUi())
    val rejectUi = _rejectUi.asStateFlow()

    // 正在解除中的条目 id（用于防止重复提交）；null = 空闲
    private val _removingId = MutableStateFlow<Long?>(null)
    val removingId = _removingId.asStateFlow()

    // 解除失败的提示文案；非空时页面弹错误对话框，关闭后由 consumeRemoveError 清空
    private val _removeError = MutableStateFlow<String?>(null)
    val removeError = _removeError.asStateFlow()

    // 每个名单的下一页页码（下标与 BlackedType.ordinal 对应）；不属于 UI 状态，放内存即可
    private val nextPages = intArrayOf(2, 2)

    init {
        // 进入页面同时拉两个 tab，切换 tab 时无需等待
        loadList(BlackedType.Block)
        loadList(BlackedType.Reject)
    }

    /** 切换 tab（两个列表 init 时都已发起加载，这里只切选中态）。 */
    fun select(index: Int) {
        _kind.value = index
    }

    /** 当前 tab 首屏失败后的重试入口。 */
    fun load() = loadList(currentType())

    /** 解除失败的错误提示展示完毕（对话框关闭）后清空。 */
    fun consumeRemoveError() {
        _removeError.value = null
    }

    /**
     * 解除屏蔽 / 拒绝：服务端成功后本地移除条目；
     * 失败时列表保持不变，写入 [removeError] 由页面弹窗提示。
     */
    fun remove(item: BlackedItem) {
        // 上一次解除还在进行中时忽略新的请求，避免并发写名单
        if (_removingId.value != null) return
        viewModelScope.launch {
            _removingId.value = item.id
            try {
                withContext(Dispatchers.IO) {
                    service.unblack(item.asUser.user_id, item.blackedType)
                }
                // 服务端已确认移除，本地同步删掉该条目即可
                val ui = uiFor(item.blackedType)
                ui.value = ui.value.copy(items = ui.value.items.filterNot { it.id == item.id })
                AppLog.d(TAG, "remove: ${item.user.name} 已移出 ${item.blackedType.wire} 名单")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "remove ${item.user.name} failed", e)
                _removeError.value = e.message ?: "解除失败，请稍后重试"
            } finally {
                _removingId.value = null
            }
        }
    }

    /** 当前 tab 加载下一页，按条目 id 去重后追加。 */
    fun loadMore() {
        val type = currentType()
        val ui = uiFor(type)
        val state = ui.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        val page = nextPages[type.ordinal]
        viewModelScope.launch {
            ui.value = state.copy(loadingMore = true)
            runCatching {
                withContext(Dispatchers.IO) { service.fetchBlacked(type, page) }
            }.onSuccess { result ->
                nextPages[type.ordinal] = page + 1
                // 按条目 id 去重：服务端游标在增删后可能位移，翻页偶尔会重叠
                val merged = state.items + result.items.filter { new ->
                    state.items.none { it.id == new.id }
                }
                ui.value = ui.value.copy(items = merged, hasMore = result.hasMore, loadingMore = false)
                AppLog.d(TAG, "loadMore type=${type.wire} page=$page: +${result.items.size} 条，hasMore=${result.hasMore}")
            }.onFailure { e ->
                if (e is CancellationException) throw e
                // 翻页失败不打断已有列表（区别于首屏失败），只记录日志并复位加载态
                AppLog.e(TAG, "loadMore type=${type.wire} page=$page failed", e)
                ui.value = ui.value.copy(loadingMore = false)
            }
        }
    }

    private fun currentType(): BlackedType =
        if (_kind.value == 0) BlackedType.Block else BlackedType.Reject

    private fun uiFor(type: BlackedType): MutableStateFlow<BlackedListUi> =
        if (type == BlackedType.Block) _blockUi else _rejectUi

    /** 首屏加载或失败重试：回到第一页整页替换。 */
    private fun loadList(type: BlackedType) {
        val ui = uiFor(type)
        if (ui.value.loading) return
        viewModelScope.launch {
            ui.value = ui.value.copy(loading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { service.fetchBlacked(type, 1) }
            }.onSuccess { page ->
                nextPages[type.ordinal] = 2
                ui.value = ui.value.copy(items = page.items, hasMore = page.hasMore, loading = false)
                AppLog.d(TAG, "load type=${type.wire}: ${page.items.size} 条，hasMore=${page.hasMore}")
            }.onFailure { e ->
                if (e is CancellationException) throw e
                AppLog.e(TAG, "load type=${type.wire} failed", e)
                ui.value = ui.value.copy(loading = false, error = e.message ?: "名单加载失败")
            }
        }
    }

    private companion object {
        const val TAG = "Blocklist"
    }
}
