package org.lsposed.lspatch.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class FileUtils {

    public static String readTextFromInputStream(InputStream is) {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8); BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String str;
            while ((str = bufferedReader.readLine()) != null) {
                builder.append(str);
            }
            return builder.toString();
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static long calculateCrc(InputStream is) throws IOException {
        CRC32 crcMaker = new CRC32();
        byte[] buffer = new byte[65536];
        int bytesRead;
        while((bytesRead = is.read(buffer)) != -1) {
            crcMaker.update(buffer, 0, bytesRead);
        }
        return crcMaker.getValue();
    }
}