package org.lsposed.lspatch.manager

import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.lsposed.lspd.models.Module
import org.lsposed.lspd.service.ILSPApplicationService

object ManagerService : ILSPApplicationService.Stub() {

    override fun requestModuleBinder(name: String): IBinder {
        TODO("Not yet implemented")
    }

    override fun getModulesList(): List<Module> {
        return ModuleProvider.allModules
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
