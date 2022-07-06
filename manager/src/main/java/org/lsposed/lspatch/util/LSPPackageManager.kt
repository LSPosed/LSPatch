package org.lsposed.lspatch.util

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import hidden.HiddenApiBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.lsposed.lspatch.Constants.PATCH_FILE_SUFFIX
import org.lsposed.lspatch.config.ConfigManager
import org.lsposed.lspatch.config.Configs
import org.lsposed.lspatch.lspApp
import org.lsposed.patch.util.ManifestParser
import java.io.File
import java.io.IOException
import java.text.Collator
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipFile
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object LSPPackageManager {

    private const val TAG = "LSPPackageManager"

    @Parcelize
    class AppInfo(val app: ApplicationInfo, val label: String) : Parcelable {
        val isXposedModule: Boolean
            get() = app.metaData?.get("xposedminversion") != null
    }

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
            collection.sortWith(compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::label))
            val modules = buildMap {
                collection.forEach { if (it.isXposedModule) put(it.app.packageName, it.app.sourceDir) }
            }
            ConfigManager.updateModules(modules)
            appList = collection
        }
    }

    fun getIcon(appInfo: AppInfo) = appIcon[appInfo.app.packageName]!!

    suspend fun cleanTmpApkDir() {
        withContext(Dispatchers.IO) {
            lspApp.tmpApkDir.listFiles()?.forEach(File::delete)
        }
    }

    suspend fun install(): Pair<Int, String?> {
        Log.i(TAG, "Perform install patched apks")
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
                    val uri = Configs.storageDirectory?.toUri() ?: throw IOException("Uri is null")
                    val root = DocumentFile.fromTreeUri(lspApp, uri) ?: throw IOException("DocumentFile is null")
                    root.listFiles().forEach { file ->
                        if (file.name?.endsWith(PATCH_FILE_SUFFIX) != true) return@forEach
                        Log.d(TAG, "Add ${file.name}")
                        val input = lspApp.contentResolver.openInputStream(file.uri)
                            ?: throw IOException("Cannot open input stream")
                        input.use {
                            session.openWrite(file.name!!, 0, input.available().toLong()).use { output ->
                                input.copyTo(output)
                                session.fsync(output)
                            }
                        }
                    }
                    var result: Intent? = null
                    suspendCoroutine { cont ->
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
                suspendCoroutine { cont ->
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

    suspend fun getAppInfoFromApks(apks: List<Uri>): Result<AppInfo> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val app = ApplicationInfo()
                if (apks.size > 1) app.splitSourceDirs = Array<String?>(apks.size - 1) { null }
                apks.forEachIndexed { index, uri ->
                    val src = DocumentFile.fromSingleUri(lspApp, uri)
                        ?: throw IOException("DocumentFile is null")
                    val dst = lspApp.tmpApkDir.resolve(src.name!!)
                    val input = lspApp.contentResolver.openInputStream(uri)
                        ?: throw IOException("InputStream is null")
                    input.use {
                        dst.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    ZipFile(dst).use { zip ->
                        val entry = zip.getEntry("AndroidManifest.xml")
                            ?: throw IOException("AndroidManifest.xml is not found")
                        zip.getInputStream(entry).use {
                            val info = ManifestParser.parseManifestFile(it)
                            if (app.packageName != null && app.packageName != info.packageName) {
                                throw IOException("Selected apks are not of the same app")
                            }
                            app.packageName = info.packageName
                        }
                    }
                    if (index == 0) app.sourceDir = dst.absolutePath
                    else app.splitSourceDirs[index - 1] = dst.absolutePath
                }
                AppInfo(app, app.packageName)
            }.recoverCatching { t ->
                cleanTmpApkDir()
                Log.e(TAG, "Failed to load apks", t)
                throw t
            }
        }
    }
}
