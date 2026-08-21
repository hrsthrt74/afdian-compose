package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.data.content.AfadianAccount
import com.hrsthrt74.afdian.data.content.AfadianProfile
import com.hrsthrt74.afdian.data.content.SponsoringItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    private val _posts = MutableStateFlow<List<RecommendedPost>>(emptyList())
    val posts = _posts.asStateFlow()
    private val _account = MutableStateFlow<AfadianAccount?>(null)
    val account = _account.asStateFlow()
    private val _profile = MutableStateFlow<AfadianProfile?>(null)
    val profile = _profile.asStateFlow()
    private val _sponsoring = MutableStateFlow<List<SponsoringItem>>(emptyList())
    val sponsoring = _sponsoring.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()
    private var nextPublishSN = ""

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                        val accountDeferred = async { service.fetchAccount() }
                        val profileDeferred = async { service.fetchProfile() }
                        val sponsoringDeferred = async { service.fetchSponsoring() }
                        val postsDeferred = async { service.fetchRecommendedPosts() }
                        AccountResult(
                            accountDeferred.await(),
                            profileDeferred.await(),
                            sponsoringDeferred.await(),
                            postsDeferred.await()
                        )
                    }
                }
                _account.value = result.account
                _profile.value = result.profile
                _sponsoring.value = result.sponsoring
                _posts.value = result.postsPage.posts
                nextPublishSN = result.postsPage.nextPublishSN
                _hasMore.value = result.postsPage.hasMore
                Log.d("Discover", "refresh: posts=${result.postsPage.posts.size}, nextSN=$nextPublishSN, hasMore=${result.postsPage.hasMore}")
            } catch (e: Exception) {
                _error.value = e.message ?: "首页内容加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        Log.d("Discover", "loadMore called: isLoadingMore=${_isLoadingMore.value}, hasMore=${_hasMore.value}, nextSN=$nextPublishSN")
        if (_isLoadingMore.value || !_hasMore.value || nextPublishSN.isBlank()) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = withContext(Dispatchers.IO) { service.fetchRecommendedPosts(nextPublishSN) }
                Log.d("Discover", "loadMore result: newPosts=${page.posts.size}, nextSN=${page.nextPublishSN}, hasMore=${page.hasMore}")
                _posts.value = _posts.value + page.posts
                nextPublishSN = page.nextPublishSN
                _hasMore.value = page.hasMore
            } catch (e: Exception) {
                Log.e("Discover", "loadMore failed", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}

private data class AccountResult(
    val account: AfadianAccount,
    val profile: AfadianProfile,
    val sponsoring: List<SponsoringItem>,
    val postsPage: com.hrsthrt74.afdian.data.content.RecommendedPostsPage,
)
