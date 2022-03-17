package org.lsposed.lspatch.loader.util;

import org.lsposed.lspatch.loader.BuildConfig;

public class XLog {

    private static boolean enableLog = BuildConfig.DEBUG;

    public static void d(String tag, String msg) {
        if (enableLog) {
            android.util.Log.d(tag, msg);
        }
    }

    public static void v(String tag, String msg) {
        if (enableLog) {
            android.util.Log.v(tag, msg);
        }
    }

    public static void w(String tag, String msg) {
        if (enableLog) {
            android.util.Log.w(tag, msg);
        }
    }

    public static void i(String tag, String msg) {
        if (enableLog) {
            android.util.Log.i(tag, msg);
        }
    }

    public static void e(String tag, String msg) {
        if (enableLog) {
            android.util.Log.e(tag, msg);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (enableLog) {
            android.util.Log.e(tag, msg, tr);
        }
    }
}
