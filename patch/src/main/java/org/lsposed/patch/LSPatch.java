package org.lsposed.patch;

import static org.apache.commons.io.FileUtils.copyFile;

import com.wind.meditor.core.FileProcesser;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import org.lsposed.patch.base.BaseCommand;
import org.lsposed.patch.task.BuildAndSignApkTask;
import org.lsposed.patch.task.SaveApkSignatureTask;
import org.lsposed.patch.task.SaveOriginalApplicationNameTask;
import org.lsposed.patch.task.SoAndDexCopyTask;
import org.lsposed.patch.util.FileUtils;
import org.lsposed.patch.util.ManifestParser;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class LSPatch extends BaseCommand {
    private String apkPath;

    private String unzipApkFilePath;

    @Opt(opt = "o", longOpt = "output", description = "output .apk file, default is " +
            "$source_apk_dir/[file-name]-xposed-signed.apk", argName = "out-apk-file")
    private String output;

    @Opt(opt = "f", longOpt = "force", hasArg = false, description = "force overwrite")
    private boolean forceOverwrite = false;

    @Opt(opt = "pn", longOpt = "proxyname", description = "special proxy app name with full dot path", argName = "proxy app name")
    private String proxyname = "com.wind.xposed.entry.LSPApplication";

    @Opt(opt = "d", longOpt = "debuggable", description = "set 1 to make the app debuggable = true, " +
            "set 0 to make the app debuggable = false", argName = "0 or 1")
    private int debuggable = -1;  // 0: debuggable = false   1: debuggable = true

    private int dexFileCount = 0;

    private static final String UNZIP_APK_FILE_NAME = "apk-unzip-files";

    private List<Runnable> mXpatchTasks = new ArrayList<>();

    public static void main(String... args) {
        new LSPatch().doMain(args);
    }

    static public void fuckIfFail(boolean b) {
        if (!b) {
            throw new IllegalStateException("wtf", new Throwable("DUMPBT"));
        }
    }

    @Override
    protected void doCommandLine() throws IOException {
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
            System.out.println("The source apk file not exsit, please choose another one.  " +
                    "current apk file is = " + apkPath);
            return;
        }

        String currentDir = new File(".").getAbsolutePath();
        System.out.println("currentDir: " + currentDir);
        System.out.println("apkPath: " + apkPath);

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

        System.out.println("output apk path: " + output);

        String apkFileName = getBaseName(srcApkFile);

        String tempFilePath = outputApkFileParentPath + File.separator +
                currentTimeStr() + "-tmp" + File.separator;

        unzipApkFilePath = tempFilePath + apkFileName + "-" + UNZIP_APK_FILE_NAME + File.separator;

        System.out.println("outputApkFileParentPath: " + outputApkFileParentPath);
        System.out.println("unzipApkFilePath = " + unzipApkFilePath);

        // save the apk original signature info, to support crach signature.
        new SaveApkSignatureTask(apkPath, unzipApkFilePath).run();

        long currentTime = System.currentTimeMillis();
        FileUtils.decompressZip(apkPath, unzipApkFilePath);

        System.out.println("decompress apk cost time: " + (System.currentTimeMillis() - currentTime) + "ms");

        // Get the dex count in the apk zip file
        dexFileCount = findDexFileCount(unzipApkFilePath);

        System.out.println("dexFileCount: " + dexFileCount);

        String manifestFilePath = unzipApkFilePath + "AndroidManifest.xml";

        currentTime = System.currentTimeMillis();

        // parse the app main application full name from the manifest file
        ManifestParser.Pair pair = ManifestParser.parseManifestFile(manifestFilePath);
        String applicationName = null;
        if (pair != null && pair.applicationName != null) {
            applicationName = pair.applicationName;
        }

        System.out.println("Get application name cost time:  " + (System.currentTimeMillis() - currentTime) + "ms");
        System.out.println("Get the application name: " + applicationName);

        // modify manifest
        File manifestFile = new File(manifestFilePath);
        String manifestFilePathNew = unzipApkFilePath + "AndroidManifest" + "-" + currentTimeStr() + ".xml";
        File manifestFileNew = new File(manifestFilePathNew);
        fuckIfFail(manifestFile.renameTo(manifestFileNew));

        modifyManifestFile(manifestFilePathNew, manifestFilePath, applicationName);

        // new manifest may not exist
        if (manifestFile.exists() && manifestFile.length() > 0) {
            fuckIfFail(manifestFileNew.delete());
        }
        else {
            fuckIfFail(manifestFileNew.renameTo(manifestFile));
        }

        // save original main application name to asset file
        if (isNotEmpty(applicationName)) {
            mXpatchTasks.add(new SaveOriginalApplicationNameTask(applicationName, unzipApkFilePath));
        }

        //  copy xposed so and dex files into the unzipped apk
        mXpatchTasks.add(new SoAndDexCopyTask(dexFileCount, unzipApkFilePath));

        //  compress all files into an apk and then sign it.
        mXpatchTasks.add(new BuildAndSignApkTask(true, unzipApkFilePath, output));

        // copy origin apk to assets
        // convenient to bypass some check like CRC
        copyFile(srcApkFile, new File(unzipApkFilePath, "assets/origin_apk.bin"));

        // excute these tasks
        for (Runnable executor : mXpatchTasks) {
            currentTime = System.currentTimeMillis();
            executor.run();

            System.out.println(executor.getClass().getSimpleName() + " cost time: "
                    + (System.currentTimeMillis() - currentTime) + "ms");
        }

        System.out.println("Output APK: " + output);
    }

    private void modifyManifestFile(String filePath, String dstFilePath, String originalApplicationName) {
        ModificationProperty property = new ModificationProperty();
        boolean modifyEnabled = false;

        if (debuggable >= 0) {
            modifyEnabled = true;
            property.addApplicationAttribute(new AttributeItem(NodeValue.Application.DEBUGGABLE, debuggable != 0));
        }

        property.addApplicationAttribute(new AttributeItem("extractNativeLibs", true));

        modifyEnabled = true;
        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.NAME, proxyname));

        FileProcesser.processManifestFile(filePath, dstFilePath, property);
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
    @SuppressWarnings("SimpleDateFormat")
    private String currentTimeStr() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
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