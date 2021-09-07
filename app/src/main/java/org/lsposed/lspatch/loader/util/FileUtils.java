package org.lsposed.lspatch.loader.util;

import android.content.Context;

import java.io.InputStream;

public class FileUtils {

    public static String readTextFromAssets(Context context, String assetsFileName) {
        if (context == null) {
            throw new IllegalStateException("context null");
        }
        try (InputStream is = context.getAssets().open(assetsFileName)) {
            return org.lsposed.lspatch.share.FileUtils.readTextFromInputStream(is);
        } catch (Throwable ignored) {
        }
        return null;
    }
}
