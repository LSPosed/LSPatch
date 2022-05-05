package org.lsposed.lspatch

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.lsposed.lspatch.util.ShizukuApi

const val TAG = "LSPatch Manager"

lateinit var lspApp: LSPApplication

class LSPApplication : Application() {

    lateinit var prefs: SharedPreferences

    val globalScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        HiddenApiBypass.addHiddenApiExemptions("");
        lspApp = this
        lspApp.filesDir.mkdir()
        prefs = lspApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
        ShizukuApi.init()
    }
}
