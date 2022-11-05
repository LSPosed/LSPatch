package org.lsposed.lspatch.loader;

import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.Signature;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.lsposed.lspatch.loader.util.XLog;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspatch.share.PatchConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SigBypass {

    private static final String TAG = "LSPatch-SigBypass";
    private static final Map<String, String> signatures = new HashMap<>();

    private static void replaceSignature(Context context, PackageInfo packageInfo) {
        boolean hasSignature = (packageInfo.signatures != null && packageInfo.signatures.length != 0) || packageInfo.signingInfo != null;
        if (hasSignature) {
            String packageName = packageInfo.packageName;
            String replacement = signatures.get(packageName);
            if (replacement == null && !signatures.containsKey(packageName)) {
                try {
                    var metaData = context.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData;
                    String encoded = null;
                    if (metaData != null) encoded = metaData.getString("lspatch");
                    if (encoded != null) {
                        var json = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
                        var patchConfig = new Gson().fromJson(json, PatchConfig.class);
                        replacement = patchConfig.originalSignature;
                    }
                } catch (PackageManager.NameNotFoundException | JsonSyntaxException ignored) {
                }
                signatures.put(packageName, replacement);
            }
            if (replacement != null) {
                if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                    XLog.d(TAG, "Replace signature info for `" + packageName + "` (method 1)");
                    packageInfo.signatures[0] = new Signature(replacement);
                }
                if (packageInfo.signingInfo != null) {
                    XLog.d(TAG, "Replace signature info for `" + packageName + "` (method 2)");
                    Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
                    if (signaturesArray != null && signaturesArray.length > 0) {
                        signaturesArray[0] = new Signature(replacement);
                    }
                }
            }
        }
    }

    private static void hookPackageParser(Context context) {
        XposedBridge.hookAllMethods(PackageParser.class, "generatePackageInfo", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                var packageInfo = (PackageInfo) param.getResult();
                if (packageInfo == null) return;
                replaceSignature(context, packageInfo);
            }
        });
    }

    private static void proxyPackageInfoCreator(Context context) {
        Parcelable.Creator<PackageInfo> originalCreator = PackageInfo.CREATOR;
        Parcelable.Creator<PackageInfo> proxiedCreator = new Parcelable.Creator<>() {
            @Override
            public PackageInfo createFromParcel(Parcel source) {
                PackageInfo packageInfo = originalCreator.createFromParcel(source);
                replaceSignature(context, packageInfo);
                return packageInfo;
            }

            @Override
            public PackageInfo[] newArray(int size) {
                return originalCreator.newArray(size);
            }
        };
        XposedHelpers.setStaticObjectField(PackageInfo.class, "CREATOR", proxiedCreator);
        try {
            Map<?, ?> mCreators = (Map<?, ?>) XposedHelpers.getStaticObjectField(Parcel.class, "mCreators");
            mCreators.clear();
        } catch (NoSuchFieldError ignore) {
        } catch (Throwable e) {
            Log.w(TAG, "fail to clear Parcel.mCreators", e);
        }
        try {
            Map<?, ?> sPairedCreators = (Map<?, ?>) XposedHelpers.getStaticObjectField(Parcel.class, "sPairedCreators");
            sPairedCreators.clear();
        } catch (NoSuchFieldError ignore) {
        } catch (Throwable e) {
            Log.w(TAG, "fail to clear Parcel.sPairedCreators", e);
        }
    }

    static void doSigBypass(Context context, int sigBypassLevel) throws IOException {
        if (sigBypassLevel >= Constants.SIGBYPASS_LV_PM) {
            hookPackageParser(context);
            proxyPackageInfoCreator(context);
        }
        if (sigBypassLevel >= Constants.SIGBYPASS_LV_PM_OPENAT) {
            String cacheApkPath;
            try (ZipFile sourceFile = new ZipFile(context.getPackageResourcePath())) {
                cacheApkPath = context.getCacheDir() + "/lspatch/origin/" + sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH).getCrc() + ".apk";
            }
            org.lsposed.lspd.nativebridge.SigBypass.enableOpenatHook(context.getPackageResourcePath(), cacheApkPath);
        }
    }
}
