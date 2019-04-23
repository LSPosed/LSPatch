package com.storm.wind.xpatch;

import com.storm.wind.xpatch.base.BaseCommand;
import com.storm.wind.xpatch.task.ApkModifyTask;
import com.storm.wind.xpatch.task.BuildAndSignApkTask;
import com.storm.wind.xpatch.task.SaveApkSignatureTask;
import com.storm.wind.xpatch.task.SoAndDexCopyTask;
import com.storm.wind.xpatch.util.FileUtils;
import com.storm.wind.xpatch.util.ManifestParser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class MainCommand extends BaseCommand {

    private String apkPath;

    private String unzipApkFilePath;

    @Opt(opt = "o", longOpt = "output", description = "output .apk file, default is " +
            "$source_apk_dir/[file-name]-xposed-signed.apk", argName = "out-apk-file")
    private String output;   // 输出的apk文件的目录以及名称

    @Opt(opt = "f", longOpt = "force", hasArg = false, description = "force overwrite")
    private boolean forceOverwrite = false;

    @Opt(opt = "k", longOpt = "keep", hasArg = false, description = "not delete the jar file " +
            "that is changed by dex2jar and the apk zip files")
    private boolean keepBuildFiles = false;

    @Opt(opt = "l", longOpt = "log", hasArg = false, description = "show all the debug logs")
    private boolean showAllLogs = false;

    @Opt(opt = "c", longOpt = "crach", hasArg = false,
            description = "disable craching the apk's signature.")
    private boolean disableCrackSignature = false;

    @Opt(opt = "xm", longOpt = "xposed-modules", description = "the xposed mpdule files to be packaged into the apk, " +
            "multi files should be seperated by :(mac) or ;(win) ")
    private String xposedModules;

    // 原来apk中dex文件的数量
    private int dexFileCount = 0;

    private static final String UNZIP_APK_FILE_NAME = "apk-unzip-files";

    private static final String DEFAULT_APPLICATION_NAME = "android.app.Application";

    private List<Runnable> mXpatchTasks = new ArrayList<>();

    public static void main(String... args) {
        new MainCommand().doMain(args);
    }

    @Override
    protected void doCommandLine() {
        if (remainingArgs.length != 1) {
            if (remainingArgs.length == 0) {
                System.out.println("Please choose one apk file you want to process. ");
            }
            if (remainingArgs.length > 1) {
                System.out.println("This tool can only used with one apk file.");
            }
            usage();
            return;
        }

        apkPath = remainingArgs[0];

        File srcApkFile = new File(apkPath);

        if (!srcApkFile.exists()) {
            System.out.println(" The source apk file not exsit, please choose another one.  " +
                    "current apk file is = " + apkPath);
            return;
        }

        String srcApkFileParentPath = srcApkFile.getParent();
        if (srcApkFileParentPath == null) {
            String absPath = srcApkFile.getAbsolutePath();
            int index = absPath.lastIndexOf(File.separatorChar);
            srcApkFileParentPath = absPath.substring(0, index);
        }

        String currentDir = new File(".").getAbsolutePath();  // 当前命令行所在的目录
        if (showAllLogs) {
            System.out.println(" currentDir = " + currentDir + " \n  apkPath = " + apkPath);
        }

        if (output == null || output.length() == 0) {
            output = getBaseName(apkPath) + "-xposed-signed.apk";
        }

        File outputFile = new File(output);
        if (outputFile.exists() && !forceOverwrite) {
            System.err.println(output + " exists, use --force to overwrite");
            usage();
            return;
        }

        System.out.println(" !!!!! output apk path -->  " + output +
                "  disableCrackSignature --> " + disableCrackSignature);

        String apkFileName = getBaseName(srcApkFile);

        // 中间文件临时存储的位置
        String tempFilePath = srcApkFileParentPath + File.separator +
                currentTimeStr() + "-tmp" + File.separator;

        // apk文件解压的目录
        unzipApkFilePath = tempFilePath + apkFileName + "-" + UNZIP_APK_FILE_NAME + File.separator;

        if (showAllLogs) {
            System.out.println(" !!!!! srcApkFileParentPath  =  " + srcApkFileParentPath +
                    "\n unzipApkFilePath = " + unzipApkFilePath);
        }

        if (!disableCrackSignature) {
            // save the apk original signature info, to support crach signature.
            new SaveApkSignatureTask(apkPath, unzipApkFilePath).run();
        }

        // 先解压apk到指定目录下
        FileUtils.decompressZip(apkPath, unzipApkFilePath);

        // Get the dex count in the apk zip file
        dexFileCount = findDexFileCount(unzipApkFilePath);

        if (showAllLogs) {
            System.out.println(" --- dexFileCount = " + dexFileCount);
        }

        String manifestFilePath = unzipApkFilePath + "AndroidManifest.xml";

        // parse the app main application full name from the manifest file
        ManifestParser.Pair pair = ManifestParser.parseManidestFile(manifestFilePath);
        String applicationName;
        if (pair != null && pair.applictionName != null) {
            applicationName = pair.applictionName;
        } else {
            System.out.println(" Application name not found error !!!!!! ");
            applicationName = DEFAULT_APPLICATION_NAME;
        }

        if (showAllLogs) {
            System.out.println(" Get the application name --> " + applicationName);
        }

        // 1. modify the apk dex file to make xposed can run in it
        mXpatchTasks.add(new ApkModifyTask(showAllLogs, keepBuildFiles, unzipApkFilePath, applicationName,
                dexFileCount));

        // 2. copy xposed so and dex files into the unzipped apk
        mXpatchTasks.add(new SoAndDexCopyTask(dexFileCount, unzipApkFilePath, getXposedModules(xposedModules)));

        // 3. compress all files into an apk and then sign it.
        mXpatchTasks.add(new BuildAndSignApkTask(keepBuildFiles, unzipApkFilePath, output));

        // 4. excute these tasks
        for (Runnable executor : mXpatchTasks) {
            executor.run();
        }

        // 5. delete all the build files that is useless now
        File unzipApkFile = new File(unzipApkFilePath);
        if (!keepBuildFiles && unzipApkFile.exists()) {
            FileUtils.deleteDir(unzipApkFile);
        }

        File tempFile = new File(tempFilePath);
        if (!keepBuildFiles && tempFile.exists()) {
            tempFile.delete();
        }
    }

    private int findDexFileCount(String unzipApkFilePath) {
        File zipfileRoot = new File(unzipApkFilePath);
        if (!zipfileRoot.exists()) {
            return 0;
        }
        File[] childFiles = zipfileRoot.listFiles();
        if (childFiles == null || childFiles.length == 0) {
            return 0;
        }
        int count = 0;
        for (File file : childFiles) {
            String fileName = file.getName();
            if (Pattern.matches("classes.*\\.dex", fileName)) {
                count++;
            }
        }
        return count;
    }

    // Use the current timestamp as the name of the build file
    private String currentTimeStr() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");//设置日期格式
        return df.format(new Date());
    }

    private String[] getXposedModules(String modules) {
        if (modules == null || modules.isEmpty()) {
            return null;
        }
        return modules.split(File.pathSeparator);
    }
}
