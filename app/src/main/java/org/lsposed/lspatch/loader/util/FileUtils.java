package org.lsposed.lspatch.loader.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FileUtils {

    public static String readTextFromAssets(Context context, String assetsFileName) {
        if (context == null) {
            throw new IllegalStateException("context null");
        }
        try (InputStream is = context.getAssets().open(assetsFileName)) {
            return readTextFromInputStream(is);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String readTextFromInputStream(InputStream is) {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8); BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String str;
            while ((str = bufferedReader.readLine()) != null) {
                builder.append(str);
            }
            return builder.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
