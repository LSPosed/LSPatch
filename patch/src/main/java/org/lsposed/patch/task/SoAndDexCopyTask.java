package org.lsposed.patch.task;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Wind
 */
public class SoAndDexCopyTask implements Runnable {

    private final String[] APK_LIB_PATH_ARRAY = {
            "lib/armeabi-v7a/",
            "lib/armeabi/",
            "lib/arm64-v8a/",
            "lib/x86",
            "lib/x86_64"
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
        List<String> existLibPathArray = new ArrayList<>();
        for (String libPath : APK_LIB_PATH_ARRAY) {
            String apkSoFullPath = fullLibPath(libPath);
            File apkSoFullPathFile = new File(apkSoFullPath);
            if (apkSoFullPathFile.exists()) {
                existLibPathArray.add(libPath);
            } else {
                System.out.println("Target app dont have " + libPath + ", skip");
            }
        }

        if (existLibPathArray.isEmpty()) {
            System.out.println("Target app dont have any so in \"lib/\" dir, so create default \"armeabi-v7a\"");
            String libPath = APK_LIB_PATH_ARRAY[0];
            String apkSoFullPath = fullLibPath(libPath);
            File apkSoFullPathFile = new File(apkSoFullPath);
            if (!apkSoFullPathFile.mkdirs()) {
                throw new IllegalStateException("mkdir fail " + apkSoFullPathFile.getAbsolutePath());
            }
            existLibPathArray.add(libPath);
        }

        for (String libPath : existLibPathArray) {
            if (libPath == null || libPath.isEmpty()) {
                throw new IllegalStateException("fail eabi path");
            }

            String apkSoFullPath = fullLibPath(libPath);
            String eabi = libPath.substring(libPath.indexOf("/"));
            if (eabi.isEmpty()) {
                throw new IllegalStateException("fail find eabi in " + libPath);
            }

            File[] files = new File("list-so", eabi).listFiles();
            if (files == null) {
                System.out.println("Warning: Nothing so file has been copied in " + libPath);
                continue;
            }
            for (File mySoFile : files) {
                File target = new File(apkSoFullPath, mySoFile.getName());
                try {
                    FileUtils.copyFile(mySoFile, target);
                } catch (Exception err) {
                    throw new IllegalStateException("wtf", err);
                }
                System.out.println("Copy " + mySoFile.getAbsolutePath() + " to " + target.getAbsolutePath());
            }
        }
    }

    private void copyDexFile(int dexFileCount) {
        try {
            // copy all dex files in list-dex
            File[] files = new File("list-dex").listFiles();
            if (files == null || files.length == 0) {
                System.out.println("Warning: Nothing dex file has been copied");
                return;
            }
            for (File file : files) {
                String copiedDexFileName = "classes" + (dexFileCount + 1) + ".dex";
                File target = new File(unzipApkFilePath, copiedDexFileName);
                FileUtils.copyFile(file, target);
                System.out.println("Copy " + file.getAbsolutePath() + " to " + target.getAbsolutePath());
                dexFileCount++;
            }
        } catch (Exception err) {
            throw new IllegalStateException("wtf", err);
        }
    }

    private String fullLibPath(String libPath) {
        return unzipApkFilePath + libPath.replace("/", File.separator);
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
