package org.lsposed.lspatch.ui.util

import android.util.Log
import androidx.compose.runtime.mutableStateListOf

class ManagerLogging {
    class LogEntry(val level: Int, val tag: String, val str: String)

    companion object {
        @JvmStatic
        val logs = mutableStateListOf<LogEntry>()


        private fun addLog(
            level: Int,
            tag: String,
            str: String
        ) {
            logs += LogEntry(level, tag, str)
            preventOverflow()
        }

        @JvmStatic
        fun d(
            tag: String,
            msg: String
        ) {
            addLog(
                Log.DEBUG,
                tag,
                msg
            )
            Log.d(tag, msg)
        }

        @JvmStatic
        fun i(
            tag: String,
            msg: String
        ) {
            addLog(
                Log.INFO,
                tag,
                msg
            )
            Log.i(tag, msg)
        }

        @JvmStatic
        fun w(
            tag: String,
            msg: String,
            throwable: Throwable
        ) {
            addLog(
                Log.WARN,
                tag,
                "$msg -- ${throwable.message}"
            )
            Log.w(tag, msg, throwable)
        }

        @JvmStatic
        fun w(
            tag: String,
            msg: String
        ) {
            addLog(
                Log.WARN,
                tag,
                msg
            )
            Log.e(tag, msg)
        }

        @JvmStatic
        fun e(
            tag: String,
            msg: String
        ) {
            addLog(
                Log.ERROR,
                tag,
                msg
            )
            Log.e(tag, msg)
        }

        @JvmStatic
        fun e(
            tag: String,
            throwable: Throwable
        ) {
            e(tag, throwable.message ?: "Unknown error")
        }


        @JvmStatic
        fun e(
            tag: String,
            msg: String,
            throwable: Throwable
        ) {
            e(tag, "$msg -- ${throwable.message ?: "Unknown error"}")
        }

        private fun preventOverflow() {
            if (logs.size > 300 /* limit to 300 logs*/ /* TODO: make configurable */) {
                logs.removeAt(0) // prevent memory leak
            }
        }
    }
}