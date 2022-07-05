package org.lsposed.lspatch

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.Constants.PATCH_FILE_SUFFIX
import org.lsposed.lspatch.Constants.PREFS_STORAGE_DIRECTORY
import org.lsposed.lspatch.config.MyKeyStore
import org.lsposed.lspatch.share.PatchConfig
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger
import java.io.IOException

object Patcher {

    class Options(
        private val verbose: Boolean,
        private val config: PatchConfig,
        private val apkPaths: List<String>,
        private val embeddedModules: List<String>?
    ) {
        fun toStringArray(): Array<String> {
            return buildList {
                addAll(apkPaths)
                add("-o"); add(lspApp.tmpApkDir.absolutePath)
                if (config.debuggable) add("-d")
                add("-l"); add(config.sigBypassLevel.toString())
                add("--v1"); add(config.v1.toString())
                add("--v2"); add(config.v2.toString())
                if (config.useManager) add("--manager")
                if (config.overrideVersionCode) add("-r")
                if (verbose) add("-v")
                embeddedModules?.forEach {
                    add("-m"); add(it)
                }
                if (!MyKeyStore.useDefault) {
                    addAll(arrayOf("-k", MyKeyStore.file.path, MyKeyStore.password, MyKeyStore.alias, MyKeyStore.aliasPassword))
                }
            }.toTypedArray()
        }
    }

    suspend fun patch(logger: Logger, options: Options) {
        withContext(Dispatchers.IO) {
            LSPatch(logger, *options.toStringArray()).doCommandLine()

            val uri = lspApp.prefs.getString(PREFS_STORAGE_DIRECTORY, null)?.toUri()
                ?: throw IOException("Uri is null")
            val root = DocumentFile.fromTreeUri(lspApp, uri)
                ?: throw IOException("DocumentFile is null")
            root.listFiles().forEach {
                if (it.name?.endsWith(PATCH_FILE_SUFFIX) == true) it.delete()
            }
            lspApp.tmpApkDir.walk()
                .filter { it.name.endsWith(PATCH_FILE_SUFFIX) }
                .forEach { apk ->
                    val file = root.createFile("application/vnd.android.package-archive", apk.name)
                        ?: throw IOException("Failed to create output file")
                    val output = lspApp.contentResolver.openOutputStream(file.uri)
                        ?: throw IOException("Failed to open output stream")
                    output.use {
                        apk.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            logger.i("Patched files are saved to ${root.uri.lastPathSegment}")
        }
    }
}
