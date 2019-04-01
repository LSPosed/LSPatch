package com.storm.wind.xpatch.task;

import com.storm.wind.xpatch.util.FileUtils;

import java.io.File;
import java.util.HashMap;

/**
 * Created by Wind
 */
public class SoAndDexCopyTask implements Runnable {

    private static final String SO_FILE_NAME = "libxpatch_wl.so";

    private final String[] APK_LIB_PATH_ARRAY = {
            "lib/armeabi-v7a/",
            "lib/armeabi/",
            "lib/arm64-v8a/"
    };

    private final HashMap<String, String> SO_FILE_PATH_MAP = new HashMap<String, String>() {
        {
            put(APK_LIB_PATH_ARRAY[0], "assets/" + APK_LIB_PATH_ARRAY[0] + SO_FILE_NAME);
            put(APK_LIB_PATH_ARRAY[1], "assets/" + APK_LIB_PATH_ARRAY[0] + SO_FILE_NAME);
            put(APK_LIB_PATH_ARRAY[2], "assets/" + APK_LIB_PATH_ARRAY[2] + SO_FILE_NAME);
        }
    };

    private int dexFileCount;
    private String unzipApkFilePath;

    public SoAndDexCopyTask(int dexFileCount, String unzipApkFilePath) {
        this.dexFileCount = dexFileCount;
        this.unzipApkFilePath = unzipApkFilePath;
    }

    @Override
    public void run() {
        // 复制xposed兼容层的dex文件以及so文件到当前目录下
        copySoFile();
        copyDexFile(dexFileCount);

        // 删除签名信息
        deleteMetaInfo();
    }

    private void copySoFile() {
        // Try to find so file path in the apk, then copy so into it
        boolean copySuccess = false;
        for (String libPath : APK_LIB_PATH_ARRAY) {
            boolean copied = copyLibFile(unzipApkFilePath + libPath.replace("/", File.separator),
                    SO_FILE_PATH_MAP.get(libPath), false);
            if (copied) {
                copySuccess = true;
            }
        }

        // Iif apk do not contain so file path, then create lib/armeabi-v7a, and copy libwhale.so into it
        if (!copySuccess) {
            String path = APK_LIB_PATH_ARRAY[0];
            copySuccess = copyLibFile(unzipApkFilePath + path.replace("/", File.separator),
                    SO_FILE_PATH_MAP.get(path), true);
        }
        if (!copySuccess) {
            throw new IllegalArgumentException(" copy so file failed ");
        }
    }

    private void copyDexFile(int dexFileCount) {
        //  copy dex file to root dir, rename it first
        String copiedDexFileName = "classes" + (dexFileCount + 1) + ".dex";
        // assets/classes.dex分隔符不能使用File.seperater,否则在windows上无法读取到文件，IOException
        FileUtils.copyFileFromJar("assets/classes.dex", unzipApkFilePath + copiedDexFileName);
    }

    private boolean copyLibFile(String libFilePath, String srcSoPath, boolean forceCopy) {
        File apkSoParentFile = new File(libFilePath);
        if (forceCopy && !apkSoParentFile.exists()) {
            apkSoParentFile.mkdirs();
        }

        File[] childs = apkSoParentFile.listFiles();
        if (apkSoParentFile.exists() && (forceCopy || (childs != null && childs.length > 0))) {
            FileUtils.copyFileFromJar(srcSoPath, new File(apkSoParentFile, SO_FILE_NAME).getAbsolutePath());
            return true;
        }
        return false;
    }

    private void deleteMetaInfo() {
        String metaInfoFilePath = "META-INF";
        File metaInfoFileRoot = new File(unzipApkFilePath + metaInfoFilePath);
        if (!metaInfoFileRoot.exists()) {
            return;
        }
        File[] childFileList = metaInfoFileRoot.listFiles();
        if (childFileList == null || childFileList.length == 0) {
            return;
        }
        for (File file : childFileList) {
            String fileName = file.getName().toUpperCase();
            if (fileName.endsWith(".MF") || fileName.endsWith(".RAS") || fileName.endsWith(".SF")) {
                file.delete();
            }
        }
    }
}
