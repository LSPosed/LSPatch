package org.lsposed.lspatch

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku

const val TAG = "LSPatch Manager"

class LSPApplication : Application() {

    companion object {
        var shizukuBinderAvalable = false
        var shizukuGranted by mutableStateOf(false)

        lateinit var appContext: Context
        lateinit var prefs: SharedPreferences

        init {
            Shizuku.addBinderReceivedListenerSticky {
                shizukuBinderAvalable = true
                shizukuGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
            Shizuku.addBinderDeadListener {
                shizukuBinderAvalable = false
                shizukuGranted = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        appContext.filesDir.mkdir()
        prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
}
