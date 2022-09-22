package org.lsposed.lspatch.util

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import hidden.HiddenApiBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.appiconloader.AppIconLoader
import org.lsposed.lspatch.Constants.PATCH_FILE_SUFFIX
import org.lsposed.lspatch.config.ConfigManager
import org.lsposed.lspatch.config.Configs
import org.lsposed.lspatch.lspApp
import java.io.File
import java.io.IOException
import java.text.Collator
import java.util.*
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

    @SuppressLint("StaticFieldLeak")
    private val iconLoader = AppIconLoader(lspApp.resources.getDimensionPixelSize(android.R.dimen.app_icon_size), false, lspApp)
    private val appIcon = mutableMapOf<String, ImageBitmap>()

    suspend fun fetchAppList() {
        withContext(Dispatchers.IO) {
            val pm = lspApp.packageManager
            val collection = mutableListOf<AppInfo>()
            pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach {
                val label = pm.getApplicationLabel(it)
                collection.add(AppInfo(it, label.toString()))
                appIcon[it.packageName] = iconLoader.loadIcon(it).asImageBitmap()
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
                        val adapter = IntentSenderHelper.IIntentSenderAdaptor { intent ->
                            result = intent
                            cont.resume(Unit)
                        }
                        val intentSender = IntentSenderHelper.newIntentSender(adapter)
                        session.commit(intentSender)
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
                    val adapter = IntentSenderHelper.IIntentSenderAdaptor { intent ->
                        result = intent
                        cont.resume(Unit)
                    }
                    val intentSender = IntentSenderHelper.newIntentSender(adapter)
                    ShizukuApi.uninstallPackage(packageName, intentSender)
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
                var primary: ApplicationInfo? = null
                val splits = apks.mapNotNull { uri ->
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
                    if (primary == null) {
                        primary = lspApp.packageManager.getPackageArchiveInfo(dst.absolutePath, 0)?.applicationInfo
                        primary?.let {
                            it.sourceDir = dst.absolutePath
                            return@mapNotNull null
                        }
                    }
                    dst.absolutePath
                }

                // TODO: Check selected apks are from the same app
                if (primary == null) throw IllegalArgumentException("No primary apk")
                val label = lspApp.packageManager.getApplicationLabel(primary!!).toString()
                if (splits.isNotEmpty()) primary!!.splitSourceDirs = splits.toTypedArray()
                AppInfo(primary!!, label)
            }.recoverCatching { t ->
                cleanTmpApkDir()
                Log.e(TAG, "Failed to load apks", t)
                throw t
            }
        }
    }

    fun getLaunchIntentForPackage(packageName: String): Intent? {
        val intentToResolve = Intent(Intent.ACTION_MAIN)
        intentToResolve.addCategory(Intent.CATEGORY_INFO)
        intentToResolve.setPackage(packageName)
        var ris = lspApp.packageManager.queryIntentActivities(intentToResolve, 0)

        if (ris.size <= 0) {
            intentToResolve.removeCategory(Intent.CATEGORY_INFO)
            intentToResolve.addCategory(Intent.CATEGORY_LAUNCHER)
            intentToResolve.setPackage(packageName)
            ris = lspApp.packageManager.queryIntentActivities(intentToResolve, 0)
        }

        if (ris.size <= 0) return null

        return Intent(intentToResolve)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setClassName(
                ris[0].activityInfo.packageName,
                ris[0].activityInfo.name
            )
    }
}
