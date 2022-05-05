package org.lsposed.lspatch.util

import android.content.IntentSender
import android.content.pm.*
import android.os.IBinder
import android.os.IInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuApi {

    private fun IBinder.wrap() = ShizukuBinderWrapper(this)
    private fun IInterface.asShizukuBinder() = this.asBinder().wrap()

    private val iPackageManager: IPackageManager by lazy {
        IPackageManager.Stub.asInterface(SystemServiceHelper.getSystemService("package").wrap())
    }

    private val iPackageInstaller: IPackageInstaller by lazy {
        IPackageInstaller.Stub.asInterface(iPackageManager.packageInstaller.asShizukuBinder())
    }

    private val packageInstaller: PackageInstaller by lazy {
        PackageInstaller::class.java.getConstructor(IPackageInstaller::class.java, String::class.java, Int::class.javaPrimitiveType)
            .newInstance(iPackageInstaller, "com.android.shell", 0)
    }

    var isBinderAvalable = false
    var isPermissionGranted by mutableStateOf(false)

    fun init() {
        Shizuku.addBinderReceivedListenerSticky {
            isBinderAvalable = true
            isPermissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
        Shizuku.addBinderDeadListener {
            isBinderAvalable = false
            isPermissionGranted = false
        }
    }

    fun createPackageInstallerSession(params: PackageInstaller.SessionParams): PackageInstaller.Session {
        val sessionId = packageInstaller.createSession(params)
        val iSession = IPackageInstallerSession.Stub.asInterface(iPackageInstaller.openSession(sessionId).asShizukuBinder())
        val constructor by lazy { PackageInstaller.Session::class.java.getConstructor(IPackageInstallerSession::class.java) }
        return constructor.newInstance(iSession)
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return iPackageManager.getPackageInfo(packageName, 0, 0 /* TODO: userId */) != null
    }

    fun uninstallPackage(packageName: String, intentSender: IntentSender) {
        packageInstaller.uninstall(packageName, intentSender)
    }
}
