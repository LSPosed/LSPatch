package org.lsposed.lspatch.ui.viewmodel.manage

import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import org.lsposed.lspatch.util.LSPPackageManager

class ModuleManageViewModel : ViewModel() {

    companion object {
        private const val TAG = "ModuleManageViewModel"
    }

    class XposedInfo(
        val api: Int,
        val description: String,
        val scope: List<String>
    )

    val appList: List<Pair<LSPPackageManager.AppInfo, XposedInfo>> by derivedStateOf {
        LSPPackageManager.appList.mapNotNull { appInfo ->
            val metaData = appInfo.app.metaData ?: return@mapNotNull null
            appInfo to XposedInfo(
                metaData.getInt("xposedminversion", -1).also { if (it == -1) return@mapNotNull null },
                metaData.getString("xposeddescription") ?: "",
                emptyList() // TODO: scope
            )
        }.also {
            Log.d(TAG, "Loaded ${it.size} Xposed modules")
        }
    }
}
