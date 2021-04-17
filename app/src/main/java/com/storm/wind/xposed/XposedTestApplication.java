package com.storm.wind.xposed;

import static com.wind.xposed.entry.LSPLoader.initAndLoadModules;

import android.app.Application;
import android.content.Context;

import org.lsposed.lspd.yahfa.hooker.YahfaHooker;

import de.robv.android.xposed.XposedInit;

// you can run this app to test hook framework
public class XposedTestApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    static {
        System.loadLibrary("lspd");
        YahfaHooker.init();
        XposedInit.startsSystemServer = false;
        initAndLoadModules();
    }
}
