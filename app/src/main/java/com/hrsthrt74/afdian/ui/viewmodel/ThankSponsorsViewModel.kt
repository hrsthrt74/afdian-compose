package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.ThankSponsor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth

/**
 * 赞助者感谢页的 ViewModel：封装 get-thank-sponsors 的加载与分页
 *
 * @param userId 创作者自己的 user_id（接口必填，来自当前登录账号的资料）
 * @param initialYear 进入页面时指定的年份，为 null 时取上个月所在年
 * @param initialMonth 进入页面时指定的月份，为 null 时取上个月
 *   （感谢页语义是感谢上一批赞助者，缺省兜底为上个月；「每月汇总」页会按行传入具体年月）
 */
class ThankSponsorsViewModel(
    application: Application,
    private val userId: String,
    initialYear: Int? = null,
    initialMonth: Int? = null,
) : AndroidViewModel(application) {
    // 当前正在查看的年月：优先用外部传入的年月，否则默认上个月；jumpTo 可切换（外部只读）
    var year: Int = initialYear ?: YearMonth.now().minusMonths(1).year
        private set
    var month: Int = initialMonth ?: YearMonth.now().minusMonths(1).monthValue
        private set

    private val service = AfadianContentService(AuthTokenStore(application))

    private val _sponsors = MutableStateFlow<List<ThankSponsor>>(emptyList())
    val sponsors = _sponsors.asStateFlow()
    // 标题里的人数标签文案，来自接口 total_count（如「所有」）
    private val _totalLabel = MutableStateFlow("所有")
    val totalLabel = _totalLabel.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()
    // 感谢语（服务端最新值）；与年月无关，进页面时拉一次
    private val _thankWord = MutableStateFlow("")
    val thankWord = _thankWord.asStateFlow()
    private val _isSavingWord = MutableStateFlow(false)
    val isSavingWord = _isSavingWord.asStateFlow()
    // 下一页页码（接口从 1 开始）
    private var nextPage = 1

    init {
        load()
        loadThankWord()
    }

    /** 读取感谢语：失败不打扰主流程，输入框保持空（占位文案提示输入） */
    private fun loadThankWord() {
        if (userId.isBlank()) return
        viewModelScope.launch {
            try {
                val word = withContext(Dispatchers.IO) {
                    service.fetchThankWord(userId, year, month)
                }
                _thankWord.value = word
                AppLog.d("ThankSponsors", "感谢语加载成功，长度=${word.length}")
            } catch (e: Exception) {
                AppLog.w("ThankSponsors", "感谢语加载失败", e)
            }
        }
    }

    /** 保存感谢语：成功后把服务端值同步为刚保存的内容 */
    fun saveThankWord(content: String) {
        if (_isSavingWord.value) return
        viewModelScope.launch {
            _isSavingWord.value = true
            try {
                withContext(Dispatchers.IO) { service.editThankWord(content, year, month) }
                _thankWord.value = content
                AppLog.d("ThankSponsors", "感谢语保存成功，长度=${content.length}")
            } catch (e: Exception) {
                AppLog.e("ThankSponsors", "感谢语保存失败", e)
                _error.value = e.message ?: "感谢语保存失败"
            } finally {
                _isSavingWord.value = false
            }
        }
    }

    /** 跳转到指定年月的感谢页：更新年月并重新加载第 1 页 */
    fun jumpTo(year: Int, month: Int) {
        if (year == this.year && month == this.month) return
        AppLog.d("ThankSponsors", "jumpTo: $year-$month")
        this.year = year
        this.month = month
        load()
    }

    /** 首次加载 / 下拉重试：清空列表回到第 1 页 */
    fun load() {        if (userId.isBlank()) {
            _error.value = "缺少用户信息，请先登录"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    service.fetchThankSponsors(userId, year, month, page = 1)
                }
                _sponsors.value = result.list
                _totalLabel.value = result.totalLabel
                _hasMore.value = result.hasMore
                nextPage = 2
                AppLog.d("ThankSponsors", "load: ${result.list.size} 条，hasMore=${result.hasMore}")
            } catch (e: Exception) {
                AppLog.e("ThankSponsors", "load failed", e)
                _error.value = e.message ?: "感谢页加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 加载下一页，追加到列表尾部 */
    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || userId.isBlank()) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = nextPage
                val result = withContext(Dispatchers.IO) {
                    service.fetchThankSponsors(userId, year, month, page = page)
                }
                _sponsors.value = _sponsors.value + result.list
                _hasMore.value = result.hasMore
                nextPage = page + 1
                AppLog.d("ThankSponsors", "loadMore page=$page: +${result.list.size} 条，hasMore=${result.hasMore}")
            } catch (e: Exception) {
                AppLog.e("ThankSponsors", "loadMore failed", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}
