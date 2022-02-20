package org.lsposed.lspatch

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            return arrayListOf<String>().run {
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

                toTypedArray()
            }
        }
    }

    suspend fun patch(context: Context, logger: Logger, options: Options) {
        withContext(Dispatchers.IO) {
            val download = "${Environment.DIRECTORY_DOWNLOADS}/LSPatch"
            val externalStorageDir = Environment.getExternalStoragePublicDirectory(download)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) externalStorageDir.mkdirs()
            options.outputPath = Files.createTempDirectory("patch").absolutePathString()

            LSPatch(logger, *options.toStringArray()).doCommandLine()

            File(options.outputPath)
                .walk()
                .filter { it.isFile }
                .forEach {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        it.inputStream().use { input ->
                            externalStorageDir.resolve(it.name).outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        val contentDetails = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, it.name)
                            put(MediaStore.Downloads.RELATIVE_PATH, download)
                        }
                        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentDetails)
                            ?: throw IllegalStateException("Failed to save files to Download")
                        it.inputStream().use { input ->
                            context.contentResolver.openOutputStream(uri)!!.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            logger.i("Patched files are saved to $download")
        }
    }
}
