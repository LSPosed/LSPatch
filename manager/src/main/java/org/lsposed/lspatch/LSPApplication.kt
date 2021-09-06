package org.lsposed.lspatch

import android.app.Application
import android.content.Context

class LSPApplication : Application() {
    companion object {
        const val TAG = "LSPatch Manager"
        lateinit var appContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}