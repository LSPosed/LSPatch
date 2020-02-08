package com.storm.wind.xpatch.task;

import com.storm.wind.xpatch.util.FileUtils;

import java.io.File;
import java.util.HashMap;

/**
 * Created by Wind
 */
public class SoAndDexCopyTask implements Runnable {

    private static final String SANDHOOK_SO_FILE_NAME = "libsandhook";
    private static final String WHALE_SO_FILE_NAME = "libwhale";

    private static final String SANDHOOK_SO_FILE_NAME_WITH_SUFFIX = "libsandhook.so";
    private static final String WHALE_SO_FILE_NAME_WITH_SUFFIX = "libwhale.so";

    private static final String XPOSED_MODULE_FILE_NAME_PREFIX = "libxpatch_xp_module_";
    private static final String SO_FILE_SUFFIX = ".so";

    private final String[] APK_LIB_PATH_ARRAY = {
            "lib/armeabi-v7a/",
            "lib/armeabi/",
            "lib/arm64-v8a/"
    };

    private final HashMap<String, String> mSoFilePathMap = new HashMap<>();
    private int dexFileCount;
    private String unzipApkFilePath;
    private String[] xposedModuleArray;

    private boolean useWhaleHookFramework;

    public SoAndDexCopyTask(int dexFileCount, String unzipApkFilePath,
                            String[] xposedModuleArray, boolean useWhaleHookFramework) {
        this.dexFileCount = dexFileCount;
        this.unzipApkFilePath = unzipApkFilePath;
        this.xposedModuleArray = xposedModuleArray;
        this.useWhaleHookFramework = useWhaleHookFramework;

        String soFileName;
        if (useWhaleHookFramework) {
            soFileName = WHALE_SO_FILE_NAME;
        } else {
            soFileName = SANDHOOK_SO_FILE_NAME;
        }

        mSoFilePathMap.put(APK_LIB_PATH_ARRAY[0], "assets/lib/armeabi-v7a/" + soFileName);
        mSoFilePathMap.put(APK_LIB_PATH_ARRAY[1], "assets/lib/armeabi-v7a/" + soFileName);
        mSoFilePathMap.put(APK_LIB_PATH_ARRAY[2], "assets/lib/arm64-v8a/" + soFileName);
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
        String[] existLibPathArray = new String[3];
        int arrayIndex = 0;
        for (String libPath : APK_LIB_PATH_ARRAY) {
            String apkSoFullPath = fullLibPath(libPath);
            File apkSoFullPathFile = new File(apkSoFullPath);
            if (apkSoFullPathFile.exists()) {
                existLibPathArray[arrayIndex] = libPath;
                arrayIndex++;
            }
        }

        // 不存在lib目录，则创建lib/armeabi-v7 文件夹
        if (arrayIndex == 0) {
            String libPath = APK_LIB_PATH_ARRAY[0];
            String apkSoFullPath = fullLibPath(libPath);
            File apkSoFullPathFile = new File(apkSoFullPath);
            apkSoFullPathFile.mkdirs();
            existLibPathArray[arrayIndex] = libPath;
        }

        for (String libPath : existLibPathArray) {
            if (libPath != null && !libPath.isEmpty()) {
                String apkSoFullPath = fullLibPath(libPath);
                copyLibFile(apkSoFullPath, mSoFilePathMap.get(libPath));
            }
        }

        // copy xposed modules into the lib path
        if (xposedModuleArray != null && xposedModuleArray.length > 0) {
            int index = 0;
            for (String modulePath : xposedModuleArray) {
                modulePath = modulePath.trim();
                if (modulePath == null || modulePath.length() == 0) {
                    continue;
                }
                File moduleFile = new File(modulePath);
                if (!moduleFile.exists()) {
                    continue;
                }
                for (String libPath : existLibPathArray) {
                    if (libPath != null && !libPath.isEmpty()) {
                        String apkSoFullPath = fullLibPath(libPath);
                        String outputModuleName = XPOSED_MODULE_FILE_NAME_PREFIX + index + SO_FILE_SUFFIX;
                        File outputModuleSoFile = new File(apkSoFullPath, outputModuleName);
                        FileUtils.copyFile(moduleFile, outputModuleSoFile);
                    }
                }
                index++;
            }
        }
    }

    private void copyDexFile(int dexFileCount) {
        //  copy dex file to root dir, rename it first
        String copiedDexFileName = "classes" + (dexFileCount + 1) + ".dex";
        // assets/classes.dex分隔符不能使用File.seperater,否则在windows上无法读取到文件，IOException
        String dexAssetPath;
        if (useWhaleHookFramework) {
            dexAssetPath = "assets/dex/whale/classes-1.0.dex";
        } else {
            dexAssetPath = "assets/dex/sandhook/classes-1.0.dex";
        }
        FileUtils.copyFileFromJar(dexAssetPath, unzipApkFilePath + copiedDexFileName);
    }

    private String fullLibPath(String libPath) {
        return unzipApkFilePath + libPath.replace("/", File.separator);
    }

    private void copyLibFile(String libFilePath, String srcSoPath) {
        File apkSoParentFile = new File(libFilePath);
        if (!apkSoParentFile.exists()) {
            apkSoParentFile.mkdirs();
        }

        // get the file name first
        // int lastIndex = srcSoPath.lastIndexOf('/');
        // int length = srcSoPath.length();
        String soFileName;
        if (useWhaleHookFramework) {
            soFileName = WHALE_SO_FILE_NAME_WITH_SUFFIX;
        } else {
            soFileName = SANDHOOK_SO_FILE_NAME_WITH_SUFFIX;
        }

        // do copy
        FileUtils.copyFileFromJar(srcSoPath, new File(apkSoParentFile, soFileName).getAbsolutePath());
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
