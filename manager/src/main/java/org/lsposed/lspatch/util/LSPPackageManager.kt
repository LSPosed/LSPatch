package org.lsposed.lspatch.util

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Parcelable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import hidden.HiddenApiBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.lsposed.lspatch.Constants.PREFS_STORAGE_DIRECTORY
import org.lsposed.lspatch.lspApp
import java.io.IOException
import java.text.Collator
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object LSPPackageManager {

    @Parcelize
    class AppInfo(val app: ApplicationInfo, val label: String) : Parcelable

    const val STATUS_USER_CANCELLED = -2

    var appList by mutableStateOf(listOf<AppInfo>())
        private set

    private val appIcon = mutableMapOf<String, Drawable>()

    suspend fun fetchAppList() {
        withContext(Dispatchers.IO) {
            val pm = lspApp.packageManager
            val collection = mutableListOf<AppInfo>()
            pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach {
                val label = pm.getApplicationLabel(it)
                collection.add(AppInfo(it, label.toString()))
                appIcon[it.packageName] = pm.getApplicationIcon(it)
            }
            collection.sortWith(compareBy(Collator.getInstance(Locale.getDefault())) { it.label })
            appList = collection
        }
    }

    fun getIcon(appInfo: AppInfo) = appIcon[appInfo.app.packageName]!!

    suspend fun install(): Pair<Int, String?> {
        var status = PackageInstaller.STATUS_FAILURE
        var message: String? = null
        withContext(Dispatchers.IO) {
            runCatching {
                val params = PackageInstaller.SessionParams::class.java.getConstructor(Int::class.javaPrimitiveType)
                    .newInstance(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                var flags = HiddenApiBridge.PackageInstaller_SessionParams_installFlags(params)
                flags = flags or 0x00000004 /* PackageManager.INSTALL_ALLOW_TEST */ or 0x00000002 /* PackageManager.INSTALL_REPLACE_EXISTING */
                HiddenApiBridge.PackageInstaller_SessionParams_installFlags(params, flags)
                ShizukuApi.createPackageInstallerSession(params).use { session ->
                    val uri = lspApp.prefs.getString(PREFS_STORAGE_DIRECTORY, null)?.toUri()
                        ?: throw IOException("Uri is null")
                    val root = DocumentFile.fromTreeUri(lspApp, uri)
                        ?: throw IOException("DocumentFile is null")
                    root.listFiles().forEach { apk ->
                        val input = lspApp.contentResolver.openInputStream(apk.uri)
                            ?: throw IOException("Cannot open input stream")
                        input.use {
                            session.openWrite(apk.name!!, 0, input.available().toLong()).use { output ->
                                input.copyTo(output)
                                session.fsync(output)
                            }
                        }
                    }
                    var result: Intent? = null
                    suspendCoroutine<Unit> { cont ->
                        val countDownLatch = CountDownLatch(1)
                        val adapter = IntentSenderHelper.IIntentSenderAdaptor { intent ->
                            result = intent
                            countDownLatch.countDown()
                        }
                        val intentSender = IntentSenderHelper.newIntentSender(adapter)
                        session.commit(intentSender)
                        countDownLatch.await()
                        cont.resume(Unit)
                    }
                    result?.let {
                        status = it.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                        message = it.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    } ?: throw IOException("Intent is null")
                }
            }.onFailure {
                status = PackageInstaller.STATUS_FAILURE
                message = it.message + "\n" + it.stackTraceToString()
            }
        }
        return Pair(status, message)
    }

    suspend fun uninstall(packageName: String): Pair<Int, String?> {
        var status = PackageInstaller.STATUS_FAILURE
        var message: String? = null
        withContext(Dispatchers.IO) {
            runCatching {
                var result: Intent? = null
                suspendCoroutine<Unit> { cont ->
                    val countDownLatch = CountDownLatch(1)
                    val adapter = IntentSenderHelper.IIntentSenderAdaptor { intent ->
                        result = intent
                        countDownLatch.countDown()
                    }
                    val intentSender = IntentSenderHelper.newIntentSender(adapter)
                    ShizukuApi.uninstallPackage(packageName, intentSender)
                    countDownLatch.await()
                    cont.resume(Unit)
                }
                result?.let {
                    status = it.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    message = it.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                } ?: throw IOException("Intent is null")
            }.onFailure {
                status = PackageInstaller.STATUS_FAILURE
                message = "Exception happened\n$it"
            }
        }
        return Pair(status, message)
    }
}
