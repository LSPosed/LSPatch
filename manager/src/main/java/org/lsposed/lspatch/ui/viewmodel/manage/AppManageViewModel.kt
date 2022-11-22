package org.lsposed.lspatch.ui.viewmodel.manage

import android.content.pm.PackageInstaller
import android.util.Base64
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
import org.lsposed.lspatch.ui.page.ManagerLogs
import org.lsposed.lspatch.ui.viewstate.ProcessingState
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo
import org.lsposed.lspatch.util.ShizukuApi
import org.lsposed.patch.util.Logger
import java.io.FileNotFoundException
import java.util.zip.ZipFile

class AppManageViewModel : ViewModel() {

    companion object {
        private const val TAG = "ManageViewModel"
    }

    sealed class ViewAction {
        data class UpdateLoader(val appInfo: AppInfo, val config: PatchConfig) : ViewAction()
        object ClearUpdateLoaderResult : ViewAction()
        data class PerformOptimize(val appInfo: AppInfo) : ViewAction()
        object ClearOptimizeResult : ViewAction()
    }

    val appList: List<Pair<AppInfo, PatchConfig>> by derivedStateOf {
        LSPPackageManager.appList.mapNotNull { appInfo ->
            appInfo.app.metaData?.getString("lspatch")?.let {
                val json = Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8)
                logger.d( "Read patched config: $json")
                appInfo to Gson().fromJson(json, PatchConfig::class.java)
            }
        }.also {
            logger.d( "Loaded ${it.size} patched apps")
        }
    }

    var updateLoaderState: ProcessingState<Result<Unit>> by mutableStateOf(ProcessingState.Idle)
        private set

    var optimizeState: ProcessingState<Boolean> by mutableStateOf(ProcessingState.Idle)
        private set

    private val logger = object : Logger() {
        override fun d(msg: String) {
            if (verbose)
                ManagerLogs.d(TAG, msg)
        }

        override fun i(msg: String) {
            ManagerLogs.i(TAG, msg)
        }

        override fun e(msg: String) {
            ManagerLogs.e(TAG, msg)
        }
    }

    fun dispatch(action: ViewAction) {
        viewModelScope.launch {
            when (action) {
                is ViewAction.UpdateLoader -> updateLoader(action.appInfo, action.config)
                is ViewAction.ClearUpdateLoaderResult -> updateLoaderState = ProcessingState.Idle
                is ViewAction.PerformOptimize -> performOptimize(action.appInfo)
                is ViewAction.ClearOptimizeResult -> optimizeState = ProcessingState.Idle
            }
        }
    }

    private suspend fun updateLoader(appInfo: AppInfo, config: PatchConfig) {
        logger.i("Update loader for ${appInfo.app.packageName}")
        updateLoaderState = ProcessingState.Processing
        val result = runCatching {
            withContext(Dispatchers.IO) {
                LSPPackageManager.cleanTmpApkDir()
                val apkPaths = listOf(appInfo.app.sourceDir) + (appInfo.app.splitSourceDirs ?: emptyArray())
                val patchPaths = mutableListOf<String>()
                val embeddedModulePaths = mutableListOf<String>()
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
                ZipFile(appInfo.app.sourceDir).use { zip ->
                    zip.entries().iterator().forEach { entry ->
                        if (entry.name.startsWith(Constants.EMBEDDED_MODULES_ASSET_PATH)) {
                            val dst = lspApp.tmpApkDir.resolve(entry.name.substringAfterLast('/'))
                            embeddedModulePaths.add(dst.absolutePath)
                            zip.getInputStream(entry).use { input ->
                                dst.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
                Patcher.patch(logger, Patcher.Options(config, patchPaths, embeddedModulePaths))
                val (status, message) = LSPPackageManager.install()
                if (status != PackageInstaller.STATUS_SUCCESS) throw RuntimeException(message)
            }
        }
        updateLoaderState = ProcessingState.Done(result)
    }

    private suspend fun performOptimize(appInfo: AppInfo) {
        logger.i("Perform optimize for ${appInfo.app.packageName}")
        optimizeState = ProcessingState.Processing
        val result = withContext(Dispatchers.IO) {
            ShizukuApi.performDexOptMode(appInfo.app.packageName)
        }
        optimizeState = ProcessingState.Done(result)
    }
}