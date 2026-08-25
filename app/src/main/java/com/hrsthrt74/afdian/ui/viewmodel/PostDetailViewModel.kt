package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.RecommendedPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 动态详情页 ViewModel。
 *
 * 数据策略：路由里携带的列表页 post 先直接上屏（秒开，不转圈），
 * 后台再调 get-detail 拉取完整数据（含全文 HTML 正文）覆盖刷新。
 * 点赞/收藏使用全局共享的 [PostInteractionController]，与列表页高亮一致。
 */
class PostDetailViewModel(
    application: Application,
    initialPost: RecommendedPost,
) : AndroidViewModel(application) {

    private val service = AfadianContentService(AuthTokenStore(application))
    // 全局共享的点赞/收藏状态（与发现页、收藏页互通）
    val interactions = PostInteractionController.get(application)
    private val _post = MutableStateFlow(initialPost)
    val post = _post.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { load() }

    /** 拉取动态完整详情（全文 HTML），成功后覆盖当前展示的数据 */
    fun load() {
        // 先固定 postId，避免协程期间 _post 被覆盖后日志口径不一致
        val postId = _post.value.id
        viewModelScope.launch {
            // 调试日志：记录详情加载起点
            AppLog.d("PostDetail", "load: postId=$postId")
            runCatching {
                withContext(Dispatchers.IO) { service.fetchPostDetail(postId) }
            }.onSuccess {
                _post.value = it
                // 调试日志：只输出信息量字段（标题长度、点赞/评论数），不打印全文正文
                AppLog.d(
                    "PostDetail",
                    "load ok: postId=$postId, titleLen=${it.title.length}, likes=${it.like_count}, comments=${it.comment_count}"
                )
            }.onFailure {
                // 错误日志：用户模式下也能看到加载失败原因
                AppLog.e("PostDetail", "load failed: postId=$postId", it)
                _error.value = it.message ?: "动态详情加载失败"
            }
        }
    }
}
