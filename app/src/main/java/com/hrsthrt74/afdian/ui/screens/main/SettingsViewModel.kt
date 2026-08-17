package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val _startupPage = MutableStateFlow(store.startupPage)
    val startupPage = _startupPage.asStateFlow()

    fun setStartupPage(index: Int) {
        store.startupPage = index
        _startupPage.value = index
    }
}
