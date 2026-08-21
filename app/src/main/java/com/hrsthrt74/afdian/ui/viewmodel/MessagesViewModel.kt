package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.data.content.MessageFAQ
import com.hrsthrt74.afdian.data.content.MessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))

    private val _dialogs = MutableStateFlow<List<MessageDialog>>(emptyList())
    val dialogs = _dialogs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val hasUnreadMessages: StateFlow<Boolean> = _dialogs
        .map { dialogs -> dialogs.any { it.unread_count > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val unreadCount: StateFlow<Int> = _dialogs
        .map { dialogs -> dialogs.sumOf { it.unread_count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var currentPage = 1
    private var hasMore = true
    private var isLoadingInternal = false

    init {
        refresh()
    }

    fun refresh() {
        if (isLoadingInternal) return
        viewModelScope.launch {
            isLoadingInternal = true
            _isLoading.value = true
            _error.value = null
            currentPage = 1
            try {
                val result = withContext(Dispatchers.IO) {
                    service.fetchMessageDialogs(page = 1, unread = 0)
                }
                _dialogs.value = result.dialogs
                hasMore = result.hasMore
                currentPage = 1
            } catch (e: Exception) {
                _error.value = e.message ?: "消息列表加载失败"
            } finally {
                _isLoading.value = false
                isLoadingInternal = false
            }
        }
    }

    fun loadMore() {
        if (isLoadingInternal || !hasMore || _isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    service.fetchMessageDialogs(page = currentPage + 1, unread = 0)
                }
                _dialogs.value = _dialogs.value + result.dialogs
                hasMore = result.hasMore
                currentPage++
            } catch (e: Exception) {
                _error.value = e.message ?: "加载更多失败"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    suspend fun fetchMessages(userID: String): List<MessageItem> {
        return withContext(Dispatchers.IO) {
            service.fetchMessages(userID).messages
        }
    }

    suspend fun fetchFAQs(userID: String): List<MessageFAQ> {
        return withContext(Dispatchers.IO) {
            runCatching { service.fetchFAQs(userID) }.getOrDefault(emptyList())
        }
    }

    suspend fun sendMessage(userID: String, content: String): MessageItem {
        return withContext(Dispatchers.IO) {
            service.sendMessage(userID, content)
        }
    }

    fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(30000)
                if (!isLoadingInternal) {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            service.fetchMessageDialogs(page = 1, unread = 0)
                        }
                        _dialogs.value = result.dialogs
                        hasMore = result.hasMore
                        currentPage = 1
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
