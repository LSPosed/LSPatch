package org.lsposed.lspatch.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.lsposed.lspatch.Patcher
import org.lsposed.lspatch.share.PatchConfig
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo
import org.lsposed.patch.util.Logger

class NewPatchViewModel : ViewModel() {

    companion object {
        private const val TAG = "NewPatchViewModel"
    }

    enum class PatchState {
        INIT, SELECTING, CONFIGURING, PATCHING, FINISHED, ERROR
    }

    sealed class ViewAction {
        object DoneInit : ViewAction()
        data class ConfigurePatch(val app: AppInfo) : ViewAction()
        object SubmitPatch : ViewAction()
        object LaunchPatch : ViewAction()
    }

    var patchState by mutableStateOf(PatchState.INIT)
        private set

    var useManager by mutableStateOf(true)
    var debuggable by mutableStateOf(false)
    var overrideVersionCode by mutableStateOf(false)
    val sign = mutableStateListOf(false, true)
    var sigBypassLevel by mutableStateOf(2)
    var embeddedModules = emptyList<AppInfo>()

    lateinit var patchApp: AppInfo
        private set
    lateinit var patchOptions: Patcher.Options
        private set

    val logs = mutableStateListOf<Pair<Int, String>>()
    private val logger = object : Logger() {
        override fun d(msg: String) {
            if (verbose) {
                Log.d(TAG, msg)
                logs += Log.DEBUG to msg
            }
        }

        override fun i(msg: String) {
            Log.i(TAG, msg)
            logs += Log.INFO to msg
        }

        override fun e(msg: String) {
            Log.e(TAG, msg)
            logs += Log.ERROR to msg
        }
    }

    fun dispatch(action: ViewAction) {
        when (action) {
            is ViewAction.DoneInit -> doneInit()
            is ViewAction.ConfigurePatch -> configurePatch(action.app)
            is ViewAction.SubmitPatch -> submitPatch()
            is ViewAction.LaunchPatch -> launchPatch()
        }
    }

    private fun doneInit() {
        patchState = PatchState.SELECTING
    }

    private fun configurePatch(app: AppInfo) {
        Log.d(TAG, "Configuring patch for ${app.app.packageName}")
        patchApp = app
        patchState = PatchState.CONFIGURING
    }

    private fun submitPatch() {
        Log.d(TAG, "Submit patch")
        if (useManager) embeddedModules = emptyList()
        patchOptions = Patcher.Options(
            verbose = true,
            config = PatchConfig(useManager, debuggable, overrideVersionCode, sign[0], sign[1], sigBypassLevel, null, null),
            apkPaths = listOf(patchApp.app.sourceDir) + (patchApp.app.splitSourceDirs ?: emptyArray()),
            embeddedModules = embeddedModules.flatMap { listOf(it.app.sourceDir) + (it.app.splitSourceDirs ?: emptyArray()) }
        )
        patchState = PatchState.PATCHING
    }

    private fun launchPatch() {
        logger.i("Launch patch")
        viewModelScope.launch {
            patchState = try {
                Patcher.patch(logger, patchOptions)
                PatchState.FINISHED
            } catch (t: Throwable) {
                logger.e(t.message.orEmpty())
                logger.e(t.stackTraceToString())
                PatchState.ERROR
            } finally {
                LSPPackageManager.cleanTmpApkDir()
            }
        }
    }
}
