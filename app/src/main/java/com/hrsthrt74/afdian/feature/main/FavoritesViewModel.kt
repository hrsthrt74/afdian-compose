package com.hrsthrt74.afdian.feature.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.FavoriteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    private val _kind = MutableStateFlow(0)
    val kind = _kind.asStateFlow()
    private val _items = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val items = _items.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { load() }
    fun select(kind: Int) { if (_kind.value != kind) { _kind.value = kind; load() } }
    fun load() { viewModelScope.launch { _loading.value = true; _error.value = null; runCatching { withContext(Dispatchers.IO) { when (_kind.value) { 0 -> service.fetchMarkedCreators(); 1 -> service.fetchMarkedPosts(); else -> service.fetchMarkedCrowdfundings() } } }.onSuccess { _items.value = it }.onFailure { _error.value = it.message ?: "收藏加载失败" }; _loading.value = false } }
}
