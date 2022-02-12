package org.lsposed.lspatch.manager

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Log
import org.lsposed.lspatch.TAG
import org.lsposed.lspd.models.Module

class ModuleProvider : ContentProvider() {

    companion object {
        lateinit var allModules: List<Module>
    }

    override fun onCreate(): Boolean {
        return false
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = context!!.packageManager.getNameForUid(Binder.getCallingUid())
        Log.d(TAG, "$app calls binder")
        when (method) {
            "getBinder" -> {
                loadAllModules()
                return Bundle().apply {
                    putBinder("binder", ManagerServiceImpl())
                }
            }
            else -> throw IllegalArgumentException("Invalid method name")
        }
    }

    private fun loadAllModules() {
        val list = mutableListOf<Module>()
        for (pkg in context!!.packageManager.getInstalledPackages(PackageManager.GET_META_DATA)) {
            val app = pkg.applicationInfo ?: continue
            if (app.metaData != null && app.metaData.containsKey("xposedminversion")) {
                Module().apply {
                    apkPath = app.publicSourceDir
                    packageName = app.packageName
                    file = ModuleLoader.loadModule(apkPath)
                }.also { list.add(it) }
                Log.d(TAG, "send module ${app.packageName}")
            }
        }
        allModules = list
    }

    override fun query(p0: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? {
        return null
    }

    override fun getType(p0: Uri): String? {
        return null
    }

    override fun insert(p0: Uri, p1: ContentValues?): Uri? {
        return null
    }

    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int {
        return 0
    }

    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
        return 0
    }
}
