package org.lsposed.lspatch.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import org.lsposed.lspatch.Patcher

class NewPatchViewModel : ViewModel() {

    enum class PatchState {
        SELECTING, CONFIGURING, PATCHING, FINISHED, ERROR
    }

    var patchState by mutableStateOf(PatchState.SELECTING)
        private set
    var patchApp by mutableStateOf<AppInfo?>(null)

    var useManager by mutableStateOf(true)
    var debuggable by mutableStateOf(false)
    var overrideVersionCode by mutableStateOf(false)
    var sign = mutableStateListOf(false, true)
    var sigBypassLevel by mutableStateOf(2)

    lateinit var embeddedModules: SnapshotStateList<AppInfo>
    lateinit var patchOptions: Patcher.Options

    fun configurePatch() {
        patchState = PatchState.CONFIGURING
    }

    fun submitPatch() {
        if (useManager) embeddedModules.clear()
        patchOptions = Patcher.Options(
            apkPaths = listOf(patchApp!!.app.sourceDir) + (patchApp!!.app.splitSourceDirs ?: emptyArray()),
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

    fun finishPatch() {
        patchState = PatchState.FINISHED
    }

    fun failPatch() {
        patchState = PatchState.ERROR
    }
}
