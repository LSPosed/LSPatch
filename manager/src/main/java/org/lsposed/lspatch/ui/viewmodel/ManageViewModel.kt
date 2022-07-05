package org.lsposed.lspatch.ui.viewmodel

import android.content.pm.PackageInstaller
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.Patcher
import org.lsposed.lspatch.lspApp
import org.lsposed.lspatch.share.Constants
import org.lsposed.lspatch.share.PatchConfig
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo
import org.lsposed.patch.util.Logger
import java.io.FileNotFoundException
import java.util.zip.ZipFile

private const val TAG = "ManageViewModel"

class ManageViewModel : ViewModel() {

    sealed class ViewAction {
        data class UpdateLoader(val appInfo: AppInfo, val config: PatchConfig) : ViewAction()
        object ClearUpdateLoaderResult : ViewAction()
    }

    val appList: List<Pair<AppInfo, PatchConfig>> by derivedStateOf {
        LSPPackageManager.appList.mapNotNull { appInfo ->
            appInfo.app.metaData?.getString("lspatch")?.let {
                val json = Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8)
                Log.d(TAG, "Read patched config: $json")
                appInfo to Gson().fromJson(json, PatchConfig::class.java)
            }
        }.also {
            Log.d(TAG, "Loaded ${it.size} patched apps")
        }
    }

    var processingUpdate by mutableStateOf(false)
        private set
    var updateLoaderResult: Result<Unit>? by mutableStateOf(null)
        private set

    private val logger = object : Logger() {
        override fun d(msg: String) {
            if (verbose) Log.d(TAG, msg)
        }

        override fun i(msg: String) {
            Log.i(TAG, msg)
        }

        override fun e(msg: String) {
            Log.e(TAG, msg)
        }
    }

    fun dispatch(action: ViewAction) {
        when (action) {
            is ViewAction.UpdateLoader -> updateLoader(action.appInfo, action.config)
            is ViewAction.ClearUpdateLoaderResult -> updateLoaderResult = null
        }
    }

    private fun updateLoader(appInfo: AppInfo, config: PatchConfig) {
        Log.i(TAG, "Update loader for ${appInfo.app.packageName}")
        viewModelScope.launch {
            processingUpdate = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    LSPPackageManager.cleanTmpApkDir()
                    val apkPaths = listOf(appInfo.app.sourceDir) + (appInfo.app.splitSourceDirs ?: emptyArray())
                    val patchPaths = mutableListOf<String>()
                    val embeddedModulePaths = if (config.useManager) emptyList<String>() else null
                    for (apk in apkPaths) {
                        ZipFile(apk).use { zip ->
                            var entry = zip.getEntry(Constants.ORIGINAL_APK_ASSET_PATH)
                            if (entry == null) entry = zip.getEntry("assets/lspatch/origin_apk.bin")
                            if (entry == null) throw FileNotFoundException("Original apk entry not found for $apk")
                            zip.getInputStream(entry).use { input ->
                                val dst = lspApp.tmpApkDir.resolve(apk.substringAfterLast('/'))
                                patchPaths.add(dst.absolutePath)
                                dst.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                    Patcher.patch(
                        logger, Patcher.Options(
                            verbose = true,
                            config = config,
                            apkPaths = patchPaths,
                            embeddedModules = embeddedModulePaths
                        )
                    )
                    val (status, message) = LSPPackageManager.install()
                    if (status != PackageInstaller.STATUS_SUCCESS) throw RuntimeException(message)
                }
            }
            processingUpdate = false
            updateLoaderResult = result
        }
    }
}
