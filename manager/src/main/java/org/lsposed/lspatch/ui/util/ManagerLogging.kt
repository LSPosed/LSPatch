package org.lsposed.lspatch.ui.util

import android.util.Log
import androidx.compose.runtime.mutableStateListOf

class ManagerLogging {
    class LogEntry(val level: Int, val str: String, var number: Int)

    companion object {
        @JvmStatic
        val logs = mutableStateListOf<LogEntry>()


        private fun addLog(
            level: Int,
            str: String
        ) {
            val logEntry: LogEntry? = if (logs.size > 0) logs[logs.size - 1] else null
            if (
                logEntry != null &&
                logEntry.level == level &&
                logEntry.str == str
            ) {
                logEntry.number++
            } else {
                logs += LogEntry(level, str, 1)
                preventOverflow()
            }
        }

        @JvmStatic
        fun d(
            tag: String,
            msg: String
        ) {
            addLog(
                Log.DEBUG,
                "$tag: $msg"
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
                "$tag: $msg"
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
                Log.DEBUG,
                "$tag: $msg -- ${throwable.message}"
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
                "$tag: $msg"
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
                "$tag: $msg"
            )
            Log.e(tag, msg)
        }

        @JvmStatic
        fun e(
            tag: String,
            throwable: Throwable
        ) {
            val msg = throwable.message ?: "null"
            addLog(
                Log.ERROR,
                "$tag: $msg"
            )
            Log.e(tag, msg)
        }


        @JvmStatic
        fun e(
            tag: String,
            msg: String,
            throwable: Throwable
        ) {
            addLog(
                Log.ERROR,
                "$tag: $msg -- ${throwable.message}"
            )
            Log.e(tag, msg, throwable)
        }

        private fun preventOverflow() {
            if (logs.size > 300 /* limit to 300 logs*/ /* TODO: make configurable */) {
                logs.removeAt(0) // prevent memory leak
            }
        }
    }
}