package org.lsposed.lspatch.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.lsposed.lspatch.Patcher
import org.lsposed.lspatch.lspApp
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo
import org.lsposed.patch.util.Logger
import java.io.File

private const val TAG = "NewPatchViewModel"

class NewPatchViewModel : ViewModel() {

    enum class PatchState {
        SELECTING, CONFIGURING, PATCHING, FINISHED, ERROR
    }

    var patchState by mutableStateOf(PatchState.SELECTING)
        private set

    var useManager by mutableStateOf(true)
    var debuggable by mutableStateOf(false)
    var overrideVersionCode by mutableStateOf(false)
    val sign = mutableStateListOf(false, true)
    var sigBypassLevel by mutableStateOf(2)

    lateinit var patchApp: AppInfo
        private set
    lateinit var embeddedModules: SnapshotStateList<AppInfo>
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

    fun configurePatch(app: AppInfo) {
        Log.d(TAG, "Configuring patch for ${app.app.packageName}")
        patchApp = app
        patchState = PatchState.CONFIGURING
    }

    fun submitPatch() {
        Log.d(TAG, "Submit patch")
        if (useManager) embeddedModules.clear()
        patchOptions = Patcher.Options(
            apkPaths = listOf(patchApp.app.sourceDir) + (patchApp.app.splitSourceDirs ?: emptyArray()),
            debuggable = debuggable,
            sigbypassLevel = sigBypassLevel,
            v1 = sign[0], v2 = sign[1],
            useManager = useManager,
            overrideVersionCode = overrideVersionCode,
            verbose = true,
            embeddedModules = embeddedModules.flatMap { listOf(it.app.sourceDir) + (it.app.splitSourceDirs ?: emptyArray()) }
        )
        patchState = PatchState.PATCHING
    }

    fun launchPatch() {
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
                lspApp.tmpApkDir.listFiles()?.forEach(File::delete)
            }
        }
    }
}
