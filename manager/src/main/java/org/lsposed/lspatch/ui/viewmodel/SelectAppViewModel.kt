package org.lsposed.lspatch.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.TAG

class AppInfo(val app: ApplicationInfo, val icon: Drawable, val label: String)

class SelectAppViewModel : ViewModel() {

    init {
        Log.d(TAG, "SelectAppViewModel ${toString().substringAfterLast('@')} construct")
    }

    companion object {
        var appList by mutableStateOf(listOf<AppInfo>())
            private set
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()

    fun loadAppList(context: Context) {
        viewModelScope.launch {
            Log.d(TAG, "Start refresh apps")
            _isRefreshing.emit(true)
            val pm = context.packageManager
            val collection = mutableListOf<AppInfo>()
            withContext(Dispatchers.IO) {
                pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach {
                    val icon = pm.getApplicationIcon(it)
                    val label = pm.getApplicationLabel(it)
                    collection.add(AppInfo(it, icon, label.toString()))
                }
            }
            appList = collection
            _isRefreshing.emit(false)
            Log.d(TAG, "Refreshed ${appList.size} apps")
        }
    }
}
