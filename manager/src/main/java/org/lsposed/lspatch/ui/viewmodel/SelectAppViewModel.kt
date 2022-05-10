package org.lsposed.lspatch.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo

private const val TAG = "SelectAppViewModel"

class SelectAppsViewModel : ViewModel() {

    init {
        Log.d(TAG, "SelectAppsViewModel ${toString().substringAfterLast('@')} construct")
    }

    var isRefreshing by mutableStateOf(false)
        private set

    var filteredList by mutableStateOf(listOf<AppInfo>())
        private set

    fun filterAppList(refresh: Boolean, filter: (AppInfo) -> Boolean) {
        viewModelScope.launch {
            if (LSPPackageManager.appList.isEmpty() || refresh) {
                isRefreshing = true
                LSPPackageManager.fetchAppList()
                isRefreshing = false
            }
            filteredList = LSPPackageManager.appList.filter(filter)
            Log.d(TAG, "Filtered ${filteredList.size} apps")
        }
    }
}
