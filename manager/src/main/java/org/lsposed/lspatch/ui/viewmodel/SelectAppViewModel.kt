package org.lsposed.lspatch.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.lsposed.lspatch.TAG

@Parcelize
class AppInfo(val app: ApplicationInfo, val label: String) : Parcelable

private var appList = listOf<AppInfo>()
private val appIcon = mutableMapOf<String, Drawable>()

class SelectAppsViewModel : ViewModel() {

    init {
        Log.d(TAG, "SelectAppsViewModel ${toString().substringAfterLast('@')} construct")
    }

    var done by mutableStateOf(false)

    var isRefreshing by mutableStateOf(false)
        private set

    var filteredList by mutableStateOf(listOf<AppInfo>())
        private set

    private suspend fun refreshAppList(context: Context) {
        Log.d(TAG, "Start refresh apps")
        isRefreshing = true
        val pm = context.packageManager
        val collection = mutableListOf<AppInfo>()
        withContext(Dispatchers.IO) {
            pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach {
                val label = pm.getApplicationLabel(it)
                appIcon[it.packageName] = pm.getApplicationIcon(it)
                collection.add(AppInfo(it, label.toString()))
            }
        }
        appList = collection
        isRefreshing = false
        Log.d(TAG, "Refreshed ${appList.size} apps")
    }

    fun filterAppList(context: Context, refresh: Boolean, filter: (AppInfo) -> Boolean) {
        viewModelScope.launch {
            if (appList.isEmpty() || refresh) refreshAppList(context)
            filteredList = appList.filter(filter)
        }
    }

    fun getIcon(appInfo: AppInfo) = appIcon[appInfo.app.packageName]!!
}
