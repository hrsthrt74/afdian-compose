package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.network.AfdianRoute
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_DEFAULT
import androidx.compose.ui.graphics.Color
import com.hrsthrt74.afdian.data.settings.ThemeColorPreset
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.settings.ThemeRepository
import com.hrsthrt74.afdian.data.update.UpdateChannel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)

    // 直接把 DataStore 的 flow 转成 StateFlow，写入后自动回推 UI
    val startupPage: StateFlow<Int> = store.startupPage
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 配色模式：0=跟随系统 1=浅色 2=深色 */
    val colorMode: StateFlow<Int> = store.colorMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 高级过渡动画总开关，默认开启 */
    val advancedTransition: StateFlow<Boolean> = store.advancedTransition
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 更新通道：正式版 / Actions 构建，默认正式版 */
    val updateChannel: StateFlow<UpdateChannel> = store.updateChannel
        .stateIn(viewModelScope, SharingStarted.Eagerly, UpdateChannel.Stable)

    /** 页面内边缘滑动返回手势，默认开启 */
    val gestureBack: StateFlow<Boolean> = store.gestureBack
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 系统预测性返回开关，默认开启。 */
    val predictiveBack: StateFlow<Boolean> = store.predictiveBack
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 浏览历史记录开关，默认开启。 */
    val historyEnabled: StateFlow<Boolean> = store.historyEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 首页顶栏账号切换器开关，默认关闭；仅在已登录多账号时才在设置页露出。 */
    val homeAccountSwitcherEnabled: StateFlow<Boolean> = store.homeAccountSwitcherEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 金额类数据卡显示「元」单位，默认关闭。 */
    val showIncomeUnit: StateFlow<Boolean> = store.showIncomeUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 人数/次数类数据卡显示「人」「次」单位，默认关闭。 */
    val showCountUnit: StateFlow<Boolean> = store.showCountUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 数据卡数字滚动动画开关，默认开启。 */
    val rollingNumberEnabled: StateFlow<Boolean> = store.rollingNumberEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 数据卡数值是否使用窄体可变字体，默认关闭（关闭 = 系统默认字体）。 */
    val dataCardCustomFontEnabled: StateFlow<Boolean> = store.dataCardCustomFontEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 数据卡数值字体的 wdth（宽度）轴，默认 60。 */
    val dataCardFontWidth: StateFlow<Int> = store.dataCardFontWidth
        .stateIn(viewModelScope, SharingStarted.Eagerly, DATACARD_FONT_WIDTH_DEFAULT)

    /** 外部爱发电链接是否优先使用官方客户端，默认关闭即优先 Custom Tabs。 */
    val preferOfficialClient: StateFlow<Boolean> = store.preferOfficialClient
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * 网络优先线路，默认 ifdian.net。
     *
     * stateIn 的初值必须与 SettingsStore 的缺省值（[AfdianRoute.Default]）同源，
     * 否则下拉会先渲染默认档、等 DataStore 读回再跳一帧（即「Monet 闪一下」那类问题）。
     */
    val networkRoute: StateFlow<AfdianRoute> = store.networkRoute
        .stateIn(viewModelScope, SharingStarted.Eagerly, AfdianRoute.Default)

    fun setHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setHistoryEnabled(enabled) }
    }

    /** 切换首页账号切换器；DataStore 回推后首页顶栏立即显示/隐藏头像。 */
    fun setHomeAccountSwitcherEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setHomeAccountSwitcherEnabled(enabled) }
    }

    /** 切换底部导航栏样式（0=常规 1=悬浮），DataStore 回推后 MainScreen 立即重组生效 */
    fun setBottomBarStyle(style: Int) {
        viewModelScope.launch { store.setBottomBarStyle(style) }
    }

    fun setShowIncomeUnit(enabled: Boolean) {
        viewModelScope.launch { store.setShowIncomeUnit(enabled) }
    }

    fun setShowCountUnit(enabled: Boolean) {
        viewModelScope.launch { store.setShowCountUnit(enabled) }
    }

    /** 切换数字滚动动画；DataStore 回推后 MainScreen 更新全局开关，卡片立即切换渲染方式 */
    fun setRollingNumberEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setRollingNumberEnabled(enabled) }
    }

    /** 切换数据卡数值窄体字体；DataStore 回推后 MainActivity 更新全局字体，卡片立即换字体 */
    fun setDataCardCustomFontEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setDataCardCustomFontEnabled(enabled) }
    }

    /** 写入数据卡数值字体宽度（wdth）；只在滑块松手时调用，避免拖动过程每帧写库 */
    fun setDataCardFontWidth(width: Int) {
        viewModelScope.launch { store.setDataCardFontWidth(width) }
    }

    fun setPreferOfficialClient(enabled: Boolean) {
        viewModelScope.launch { store.setPreferOfficialClient(enabled) }
    }

    /** 切换优先线路；写入后 Application 的订阅立刻更新全局值，下一次请求就按新链走。 */
    fun setNetworkRoute(route: AfdianRoute) {
        viewModelScope.launch { store.setNetworkRoute(route) }
    }

    /**
     * 切换更新通道。
     *
     * 写库后立刻清掉上一次检查的残留结果：旧通道的「发现新版本」对话框
     * 留在新通道上会让人以为这个通道真有更新。
     */
    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            store.setUpdateChannel(channel)
            UpdateCenter.reset(getApplication())
        }
    }

    /** 重置管理页卡片布局为默认顺序；成功/失败都给 Toast 反馈，失败额外记警告日志。 */
    fun resetManagementLayout() {
        viewModelScope.launch {
            runCatching { store.resetManagementLayout() }
                .onSuccess { Toast.makeText(getApplication(), "已重置管理页卡片布局", Toast.LENGTH_SHORT).show() }
                .onFailure { error ->
                    AppLog.w("Settings", "重置管理页卡片布局失败", error)
                    Toast.makeText(getApplication(), "重置失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
        }
    }

    /** 重置首页创作者数据卡片布局为默认顺序；反馈逻辑同 [resetManagementLayout]。 */
    fun resetHomeCreatorDataLayout() {
        viewModelScope.launch {
            runCatching { store.resetHomeCreatorDataLayout() }
                .onSuccess { Toast.makeText(getApplication(), "已重置首页创作者数据卡片布局", Toast.LENGTH_SHORT).show() }
                .onFailure { error ->
                    AppLog.w("Settings", "重置首页创作者数据卡片布局失败", error)
                    Toast.makeText(getApplication(), "重置失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun setAdvancedTransition(enabled: Boolean) {
        viewModelScope.launch { store.setAdvancedTransition(enabled) }
    }

    fun setGestureBack(enabled: Boolean) {
        viewModelScope.launch { store.setGestureBack(enabled) }
    }

    fun setPredictiveBack(enabled: Boolean) {
        AppLog.d("Settings", "预测性返回已${if (enabled) "开启" else "关闭"}")
        viewModelScope.launch { store.setPredictiveBack(enabled) }
    }

    fun setColorMode(mode: Int) {
        viewModelScope.launch { store.setColorMode(mode) }
    }

    /**
     * 写入「主题」选择（false=MIUIX true=Monet）。
     *
     * 这里刻意只提供写入方、不提供读取的 StateFlow：当前选中的主题由全局主题的
     * `MiuixTheme.isDynamicColor` 驱动。因为本 VM 是进入设置页时才创建的，
     * 读 StateFlow 的初值会先按「MIUIX」渲染一帧，导致下拉闪一下。
     */
    fun setMonetEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setMonetEnabled(enabled) }
    }

    /**
     * 写入主题色。
     *
     * 同样只有写入方：外观页高亮的色球直接读全局主题下发的
     * [com.hrsthrt74.afdian.ui.theme.LocalThemeColor]，那是「真正生效」的那一档，
     * 不受本 VM 初值影响。
     */
    fun setThemeColor(preset: ThemeColorPreset) {
        viewModelScope.launch { store.setThemeColor(preset) }
    }

    /** 保存自定义主题色的具体色值（调色盘确认后调用） */
    fun setCustomThemeColor(color: Color) {
        viewModelScope.launch { store.setCustomThemeColor(color) }
    }

    fun setStartupPage(index: Int) {
        viewModelScope.launch { store.setStartupPage(index) }
    }

    fun setEnableBlur(enabled: Boolean) {
        viewModelScope.launch { ThemeRepository.setEnableBlur(getApplication(), enabled) }
    }

    fun setProgressiveBlur(enabled: Boolean) {
        viewModelScope.launch { ThemeRepository.setProgressiveBlur(getApplication(), enabled) }
    }
}
