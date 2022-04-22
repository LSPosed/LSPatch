package org.lsposed.lspatch

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.config.MyKeyStore
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger
import java.io.File
import java.nio.file.Files
import kotlin.io.path.absolutePathString

object Patcher {
    class Options(
        private val apkPaths: Array<String>,
        private val debuggable: Boolean,
        private val sigbypassLevel: Int,
        private val v1: Boolean,
        private val v2: Boolean,
        private val v3: Boolean,
        private val useManager: Boolean,
        private val overrideVersionCode: Boolean,
        private val verbose: Boolean,
        private val embeddedModules: List<String>
    ) {
        lateinit var outputPath: String

        fun toStringArray(): Array<String> {
            return buildList {
                addAll(apkPaths)
                add("-o"); add(outputPath)
                if (debuggable) add("-d")
                add("-l"); add(sigbypassLevel.toString())
                add("--v1"); add(v1.toString())
                add("--v2"); add(v2.toString())
                add("--v3"); add(v3.toString())
                if (useManager) add("--manager")
                if (overrideVersionCode) add("-r")
                if (verbose) add("-v")
                if (embeddedModules.isNotEmpty()) {
                    add("-m"); addAll(embeddedModules)
                }
                if (!MyKeyStore.useDefault) {
                    addAll(arrayOf("-k", MyKeyStore.file.path, MyKeyStore.password, MyKeyStore.alias, MyKeyStore.aliasPassword))
                }
            }.toTypedArray()
        }
    }

    suspend fun patch(context: Context, logger: Logger, options: Options) {
        withContext(Dispatchers.IO) {
            options.outputPath = Files.createTempDirectory("patch").absolutePathString()
            LSPatch(logger, *options.toStringArray()).doCommandLine()

            val uri = LSPApplication.prefs.getString(Constants.PREFS_STORAGE_DIRECTORY, null)?.toUri()
                ?: throw IllegalStateException("Uri is null")
            val root = DocumentFile.fromTreeUri(context, uri)
                ?: throw IllegalStateException("DocumentFile is null")
            root.listFiles().forEach { it.delete() }
            File(options.outputPath)
                .walk()
                .filter { it.isFile }
                .forEach {
                    val file = root.createFile("application/vnd.android.package-archive", it.name)
                        ?: throw IllegalStateException("Failed to create output file")
                    val os = context.contentResolver.openOutputStream(file.uri)
                        ?: throw IllegalStateException("Failed to open output stream")
                    os.use { output ->
                        it.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            logger.i("Patched files are saved to ${root.uri.lastPathSegment}")
        }
    }
}
