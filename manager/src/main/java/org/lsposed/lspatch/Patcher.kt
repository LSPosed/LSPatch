package org.lsposed.lspatch

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.config.Configs
import org.lsposed.lspatch.config.MyKeyStore
import org.lsposed.lspatch.share.Constants
import org.lsposed.lspatch.share.PatchConfig
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger
import java.io.IOException

object Patcher {

    class Options(
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
                if (config.useManager) add("--manager")
                if (config.overrideVersionCode) add("-r")
                if (Configs.detailPatchLogs) add("-v")
                embeddedModules?.forEach {
                    add("-m"); add(it)
                }
                if (!MyKeyStore.useDefault) {
                    addAll(arrayOf("-k", MyKeyStore.file.path, Configs.keyStorePassword, Configs.keyStoreAlias, Configs.keyStoreAliasPassword))
                }
            }.toTypedArray()
        }
    }

    suspend fun patch(logger: Logger, options: Options) {
        withContext(Dispatchers.IO) {
            LSPatch(logger, *options.toStringArray()).doCommandLine()

            val uri = Configs.storageDirectory?.toUri()
                ?: throw IOException("Uri is null")
            val root = DocumentFile.fromTreeUri(lspApp, uri)
                ?: throw IOException("DocumentFile is null")
            root.listFiles().forEach {
                if (it.name?.endsWith(Constants.PATCH_FILE_SUFFIX) == true) it.delete()
            }
            lspApp.tmpApkDir.walk()
                .filter { it.name.endsWith(Constants.PATCH_FILE_SUFFIX) }
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
