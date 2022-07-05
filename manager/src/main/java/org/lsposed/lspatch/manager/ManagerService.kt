package org.lsposed.lspatch.manager

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.lsposed.lspatch.config.ConfigManager
import org.lsposed.lspatch.lspApp
import org.lsposed.lspatch.util.AppHelper
import org.lsposed.lspd.models.Module
import org.lsposed.lspd.service.ILSPApplicationService

object ManagerService : ILSPApplicationService.Stub() {

    private const val TAG = "ManagerService"

    @SuppressLint("DiscouragedPrivateApi")
    fun startAndSendServiceBinder(packageName: String) {
        val intent = AppHelper.getSettingsIntent(packageName) ?: return
        Intent::class.java.getDeclaredMethod("putExtra", String::class.java, IBinder::class.java)
            .invoke(intent, "XposedService", XposedService())
        lspApp.startActivity(intent, null)
    }

    override fun requestModuleBinder(name: String): IBinder {
        TODO("Not yet implemented")
    }

    override fun getModulesList(): List<Module> {
        val app = lspApp.packageManager.getNameForUid(Binder.getCallingUid())
        val list = app?.let {
            runBlocking { ConfigManager.getModuleFilesForApp(it) }
        }.orEmpty()
        Log.d(TAG, "$app calls getModulesList: $list")
        return list
    }

    override fun getPrefsPath(packageName: String): String {
        TODO("Not yet implemented")
    }

    override fun requestRemotePreference(packageName: String, userId: Int, callback: IBinder?): Bundle {
        TODO("Not yet implemented")
    }

    override fun requestInjectedManagerBinder(binder: List<IBinder>?): ParcelFileDescriptor? {
        return null
    }
}
