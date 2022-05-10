package org.lsposed.lspatch

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.Constants.PATCH_FILE_SUFFIX
import org.lsposed.lspatch.Constants.PREFS_STORAGE_DIRECTORY
import org.lsposed.lspatch.config.MyKeyStore
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger
import java.io.IOException

object Patcher {

    class Options(
        private val apkPaths: List<String>,
        private val debuggable: Boolean,
        private val sigbypassLevel: Int,
        private val v1: Boolean,
        private val v2: Boolean,
        private val useManager: Boolean,
        private val overrideVersionCode: Boolean,
        private val verbose: Boolean,
        private val embeddedModules: List<String>?
    ) {
        fun toStringArray(): Array<String> {
            return buildList {
                addAll(apkPaths)
                add("-o"); add(lspApp.tmpApkDir.absolutePath)
                if (debuggable) add("-d")
                add("-l"); add(sigbypassLevel.toString())
                add("--v1"); add(v1.toString())
                add("--v2"); add(v2.toString())
                if (useManager) add("--manager")
                if (overrideVersionCode) add("-r")
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
                .filter { it.isFile }
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
