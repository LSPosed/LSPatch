package org.lsposed.lspatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger

object Patcher {
    class Options(
        private val apkPaths: Array<String>,
        private val outputPath: String,
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
        fun toStringArray(): Array<String> {
            return arrayListOf<String>().run {
                add("-f")
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

    suspend fun patch(logger: Logger, options: Options) {
        withContext(Dispatchers.IO) {
            LSPatch(logger, *options.toStringArray()).doCommandLine()
        }
    }
}
