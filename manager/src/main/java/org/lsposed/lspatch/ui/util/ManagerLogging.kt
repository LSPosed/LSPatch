package org.lsposed.lspatch.ui.util

import android.util.Log
import androidx.compose.runtime.mutableStateListOf

class ManagerLogging {
    companion object {
        @JvmStatic
        val logs = mutableStateListOf<Pair<Int, String>>()

        @JvmStatic
        fun d(
            tag: String,
            msg: String
        ) {
            logs += Log.DEBUG to "$tag: $msg"
            Log.d(tag, msg)
            preventOverflow()
        }

        @JvmStatic
        fun i(
            tag: String,
            msg: String
        ) {
            logs += Log.INFO to "$tag: $msg"
            Log.i(tag, msg)
            preventOverflow()
        }

        @JvmStatic
        fun w(
            tag: String,
            msg: String,
            throwable: Throwable
        ) {
            logs += Log.WARN to "$tag: $msg ${throwable.message}"
            Log.w(tag, msg, throwable)
            preventOverflow()
        }

        @JvmStatic
        fun w(
            tag: String,
            msg: String
        ) {
            logs += Log.WARN to "$tag: $msg"
            Log.e(tag, msg)
            preventOverflow()
        }

        @JvmStatic
        fun e(
            tag: String,
            msg: String
        ) {
            logs += Log.ERROR to "$tag: $msg"
            Log.e(tag, msg)
            preventOverflow()
        }

        @JvmStatic
        fun e(
            tag: String,
            throwable: Throwable
        ) {
            val msg = throwable.message ?: "null"
            logs += Log.ERROR to "$tag: $msg"
            Log.e(tag, msg)
            preventOverflow()
        }


        @JvmStatic
        fun e(
            tag: String,
            msg: String,
            throwable: Throwable
        ) {
            logs += Log.ERROR to "$tag: $msg -- ${throwable.message}"
            Log.e(tag, msg, throwable)
            preventOverflow()
        }

        private fun preventOverflow() {
            if (logs.size > 300 /* limit to 300 logs*/ /* TODO: make configurable */) {
                logs.removeAt(0) // prevent memory leak
            }
        }
    }
}