package org.lsposed.lspatch.ui.viewmodel

import android.util.Base64
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import org.lsposed.lspatch.share.PatchConfig
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo

private const val TAG = "ManageViewModel"

class ManageViewModel : ViewModel() {

    val appList: List<Pair<AppInfo, PatchConfig>> by derivedStateOf {
        LSPPackageManager.appList.mapNotNull { appInfo ->
            appInfo.app.metaData?.getString("lspatch")?.let {
                val json = Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8)
                appInfo to Gson().fromJson(json, PatchConfig::class.java)
            }
        }.also {
            Log.d(TAG, "Loaded ${it.size} patched apps")
        }
    }
}
