package org.lsposed.lspatch.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class NewPatchViewModel : ViewModel() {

    var useManager by mutableStateOf(true)
    var debuggable by mutableStateOf(false)
    var overrideVersionCode by mutableStateOf(false)
    var sign = mutableStateListOf(false, true, true)
    var sigBypassLevel by mutableStateOf(2)
}
