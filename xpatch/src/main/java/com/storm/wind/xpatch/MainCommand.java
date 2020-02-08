package com.storm.wind.xpatch;

import com.storm.wind.xpatch.base.BaseCommand;
import com.storm.wind.xpatch.task.ApkModifyTask;
import com.storm.wind.xpatch.task.BuildAndSignApkTask;
import com.storm.wind.xpatch.task.SaveApkSignatureTask;
import com.storm.wind.xpatch.task.SaveOriginalApplicationNameTask;
import com.storm.wind.xpatch.task.SoAndDexCopyTask;
import com.storm.wind.xpatch.util.FileUtils;
import com.storm.wind.xpatch.util.ManifestParser;
import com.wind.meditor.core.FileProcesser;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

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

    @Opt(opt = "xm", longOpt = "xposed-modules", description = "the xposed module files to be packaged into the apk, " +
            "multi files should be seperated by :(mac) or ;(win) ", argName = "xposed module file path")
    private String xposedModules;

    // 使用dex文件中插入代码的方式修改apk，而不是默认的修改Manifest中Application name的方式
    @Opt(opt = "dex", longOpt = "dex", hasArg = false, description = "insert code into the dex file, not modify manifest application name attribute")
    private boolean dexModificationMode = false;

    @Opt(opt = "pkg", longOpt = "packageName", description = "modify the apk package name", argName = "new package name")
    private String newPackageName;

    @Opt(opt = "d", longOpt = "debuggable", description = "set 1 to make the app debuggable = true, " +
            "set 0 to make the app debuggable = false", argName = "0 or 1")
    private int debuggable = -1;  // 0: debuggable = false   1: debuggable = true

    @Opt(opt = "vc", longOpt = "versionCode", description = "set the app version code",
            argName = "new-version-code")
    private int versionCode;

    @Opt(opt = "vn", longOpt = "versionName", description = "set the app version name",
            argName = "new-version-name")
    private String versionName;

    @Opt(opt = "w", longOpt = "whale", hasArg = false, description = "Change hook framework to Lody's whale")
    private boolean useWhaleHookFramework = false;   // 是否使用whale hook框架，默认使用的是SandHook

    // 原来apk中dex文件的数量
    private int dexFileCount = 0;

    private static final String UNZIP_APK_FILE_NAME = "apk-unzip-files";

    private static final String PROXY_APPLICATION_NAME = "com.wind.xpatch.proxy.XpatchProxyApplication";

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

        String outputApkFileParentPath = outputFile.getParent();
        if (outputApkFileParentPath == null) {
            String absPath = outputFile.getAbsolutePath();
            int index = absPath.lastIndexOf(File.separatorChar);
            outputApkFileParentPath = absPath.substring(0, index);
        }

        System.out.println(" !!!!! output apk path -->  " + output +
                "  disableCrackSignature --> " + disableCrackSignature);

        String apkFileName = getBaseName(srcApkFile);

        // 中间文件临时存储的位置
        String tempFilePath = outputApkFileParentPath + File.separator +
                currentTimeStr() + "-tmp" + File.separator;

        // apk文件解压的目录
        unzipApkFilePath = tempFilePath + apkFileName + "-" + UNZIP_APK_FILE_NAME + File.separator;

        if (showAllLogs) {
            System.out.println(" !!!!! outputApkFileParentPath  =  " + outputApkFileParentPath +
                    "\n unzipApkFilePath = " + unzipApkFilePath);
        }

        if (!disableCrackSignature) {
            // save the apk original signature info, to support crach signature.
            new SaveApkSignatureTask(apkPath, unzipApkFilePath).run();
        }

        // 先解压apk到指定目录下
        long currentTime = System.currentTimeMillis();
        FileUtils.decompressZip(apkPath, unzipApkFilePath);

        if (showAllLogs) {
            System.out.println(" decompress apk cost time:  " + (System.currentTimeMillis() - currentTime));
        }

        // Get the dex count in the apk zip file
        dexFileCount = findDexFileCount(unzipApkFilePath);

        if (showAllLogs) {
            System.out.println(" --- dexFileCount = " + dexFileCount);
        }

        String manifestFilePath = unzipApkFilePath + "AndroidManifest.xml";

        currentTime = System.currentTimeMillis();

        // parse the app main application full name from the manifest file
        ManifestParser.Pair pair = ManifestParser.parseManifestFile(manifestFilePath);
        String applicationName = null;
        if (pair != null && pair.applicationName != null) {
            applicationName = pair.applicationName;
        }

        if (showAllLogs) {
            System.out.println(" Get application name cost time:  " + (System.currentTimeMillis() - currentTime));
            System.out.println(" Get the application name --> " + applicationName);
        }

        // modify manifest
        File manifestFile = new File(manifestFilePath);
        String manifestFilePathNew = unzipApkFilePath  + "AndroidManifest" + "-" + currentTimeStr() + ".xml";
        File manifestFileNew = new File(manifestFilePathNew);
        manifestFile.renameTo(manifestFileNew);

        modifyManifestFile(manifestFilePathNew, manifestFilePath, applicationName);

        // new manifest may not exist
        if (manifestFile.exists() && manifestFile.length() > 0) {
            manifestFileNew.delete();
        } else {
            manifestFileNew.renameTo(manifestFile);
        }

        // save original main application name to asset file
        if (isNotEmpty(applicationName)) {
            mXpatchTasks.add(new SaveOriginalApplicationNameTask(applicationName, unzipApkFilePath));
        }

        //  modify the apk dex file to make xposed can run in it
        if (dexModificationMode && isNotEmpty(applicationName)) {
            mXpatchTasks.add(new ApkModifyTask(showAllLogs, keepBuildFiles, unzipApkFilePath, applicationName,
                    dexFileCount));
        }

        //  copy xposed so and dex files into the unzipped apk
        mXpatchTasks.add(new SoAndDexCopyTask(dexFileCount, unzipApkFilePath,
                getXposedModules(xposedModules), useWhaleHookFramework));

        //  compress all files into an apk and then sign it.
        mXpatchTasks.add(new BuildAndSignApkTask(keepBuildFiles, unzipApkFilePath, output));

        // excute these tasks
        for (Runnable executor : mXpatchTasks) {
            currentTime = System.currentTimeMillis();
            executor.run();

            if (showAllLogs) {
                System.out.println(executor.getClass().getSimpleName() + "  cost time:  "
                        + (System.currentTimeMillis() - currentTime));
            }
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

    private void modifyManifestFile(String filePath, String dstFilePath, String originalApplicationName) {
        ModificationProperty property = new ModificationProperty();
        boolean modifyEnabled = false;
        if (isNotEmpty(newPackageName)) {
            modifyEnabled = true;
            property.addManifestAttribute(new AttributeItem(NodeValue.Manifest.PACKAGE, newPackageName).setNamespace(null));
        }

        if (versionCode > 0) {
            modifyEnabled = true;
            property.addManifestAttribute(new AttributeItem(NodeValue.Manifest.VERSION_CODE, versionCode));
        }

        if (isNotEmpty(versionName)) {
            modifyEnabled = true;
            property.addManifestAttribute(new AttributeItem(NodeValue.Manifest.VERSION_NAME, versionName));
        }

        if (debuggable >= 0) {
            modifyEnabled = true;
            property.addApplicationAttribute(new AttributeItem(NodeValue.Application.DEBUGGABLE, debuggable != 0));
        }

        if (!dexModificationMode || !isNotEmpty(originalApplicationName)) {
            modifyEnabled = true;
            property.addApplicationAttribute(new AttributeItem(NodeValue.Application.NAME, PROXY_APPLICATION_NAME));
        }

        if (modifyEnabled) {
            FileProcesser.processManifestFile(filePath, dstFilePath, property);
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

    private static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }
}
