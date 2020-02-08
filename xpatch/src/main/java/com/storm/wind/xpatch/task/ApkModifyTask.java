package com.storm.wind.xpatch.task;

import com.googlecode.dex2jar.tools.Dex2jarCmd;
import com.googlecode.dex2jar.tools.Jar2Dex;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Wind
 */
public class ApkModifyTask implements Runnable {

    private static final String JAR_FILE_NAME = "output-jar.jar";

    private String unzipApkFilePath;
    private boolean keepJarFile;
    private boolean showAllLogs;
    private String applicationName;

    private int dexFileCount;

    public ApkModifyTask(boolean showAllLogs, boolean keepJarFile, String unzipApkFilePath, String applicationName, int
            dexFileCount) {
        this.showAllLogs = showAllLogs;
        this.unzipApkFilePath = unzipApkFilePath;
        this.keepJarFile = keepJarFile;
        this.applicationName = applicationName;
        this.dexFileCount = dexFileCount;
    }

    @Override
    public void run() {

        File unzipApkFile = new File(unzipApkFilePath);

        String jarOutputPath = unzipApkFile.getParent() + File.separator + JAR_FILE_NAME;

        // classes.dex
        String targetDexFileName = dumpJarFile(dexFileCount, unzipApkFilePath, jarOutputPath, applicationName);

        if (showAllLogs) {
            System.out.println("  the application class is in this dex file  = " + targetDexFileName);
        }

        String dexOutputPath = unzipApkFilePath + targetDexFileName;
        File dexFile = new File(dexOutputPath);
        if (dexFile.exists()) {
            dexFile.delete();
        }
        // 将jar转换为dex文件
        jar2DexCmd(jarOutputPath, dexOutputPath);

        // 删除掉jar文件
        File jarFile = new File(jarOutputPath);
        if (!keepJarFile && jarFile.exists()) {
            jarFile.delete();
        }

    }

    private String dumpJarFile(int dexFileCount, String dexFilePath, String jarOutputPath, String applicationName) {
        ArrayList<String> dexFileList = createClassesDotDexFileList(dexFileCount);
//        String jarOutputPath = dexFilePath + JAR_FILE_NAME;
        for (String dexFileName : dexFileList) {
            String filePath = dexFilePath + dexFileName;
            // 执行dex2jar命令，修改源代码
            boolean isApplicationClassFound = dex2JarCmd(filePath, jarOutputPath, applicationName);
            // 找到了目标应用主application的包名，说明代码注入成功，则返回当前dex文件
            if (isApplicationClassFound) {
                return dexFileName;
            }
        }
        return "";
    }

    private boolean dex2JarCmd(String dexPath, String jarOutputPath, String applicationName) {
        Dex2jarCmd cmd = new Dex2jarCmd();
        String[] args = new String[]{
                dexPath,
                "-o",
                jarOutputPath,
                "-app",
                applicationName,
                "--force"
        };
        cmd.doMain(args);

        boolean isApplicationClassFounded = cmd.isApplicationClassFounded();
        if (showAllLogs) {
            System.out.println("isApplicationClassFounded ->  " + isApplicationClassFounded + "the dexPath is  " +
                    dexPath);
        }
        return isApplicationClassFounded;
    }

    private void jar2DexCmd(String jarFilePath, String dexOutPath) {
        Jar2Dex cmd = new Jar2Dex();
        String[] args = new String[]{
                jarFilePath,
                "-o",
                dexOutPath
        };
        cmd.doMain(args);
    }

    // 列出目录下所有dex文件，classes.dex，classes2.dex，classes3.dex  .....
    private ArrayList<String> createClassesDotDexFileList(int dexFileCount) {
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < dexFileCount; i++) {
            if (i == 0) {
                list.add("classes.dex");
            } else {
                list.add("classes" + (i + 1) + ".dex");
            }
        }
        return list;
    }
}
