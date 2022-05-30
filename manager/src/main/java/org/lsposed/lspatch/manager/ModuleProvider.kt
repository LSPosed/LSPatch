package org.lsposed.lspatch.manager

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Log
import org.lsposed.lspatch.TAG
import org.lsposed.lspatch.lspApp

class ModuleProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return false
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = lspApp.packageManager.getNameForUid(Binder.getCallingUid())
        Log.d(TAG, "$app requests ModuleProvider")
        return when (method) {
            "getBinder" -> Bundle().apply {
                putBinder("binder", ManagerService.asBinder())
            }
            else -> throw IllegalArgumentException("Invalid method name")
        }
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
