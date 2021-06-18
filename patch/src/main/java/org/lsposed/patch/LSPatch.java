package org.lsposed.patch;

import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.copyFile;

import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.wind.meditor.core.FileProcesser;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.patch.base.BaseCommand;
import org.lsposed.patch.task.BuildAndSignApkTask;
import org.lsposed.patch.util.ApkSignatureHelper;
import org.lsposed.patch.util.ManifestParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class LSPatch extends BaseCommand {
    private String apkPath;

    private String unzipApkFilePath;

    @Opt(opt = "o", longOpt = "output", description = "Output .apk file, default is " +
            "$source_apk_dir/[file-name]-xposed-signed.apk", argName = "file")
    private String outputPath;

    @Opt(opt = "f", longOpt = "force", hasArg = false, description = "Force overwrite exists output file")
    private boolean forceOverwrite = false;

    @Opt(opt = "p", longOpt = "proxyname", description = "Special proxy app name with full dot path", argName = "name")
    private String proxyName = "org.lsposed.lspatch.appstub.LSPApplicationStub";

    @Opt(opt = "d", longOpt = "debuggable", description = "Set 1 to make the app debuggable = true, " +
            "set 0 to make the app debuggable = false", argName = "0 or 1")
    private int debuggableFlag = -1;  // 0: debuggable = false   1: debuggable = true

    @Opt(opt = "l", longOpt = "sigbypasslv", description = "Signature bypass level. 0 (disable), 1 (pm), 2 (pm+openat). default 0", argName = "0-2")
    private int sigbypassLevel = 0;

    private int dexFileCount = 0;

    private static final String UNZIP_APK_FILE_NAME = "apk-unzip-files";
    private static final String APPLICATION_NAME_ASSET_PATH = "assets/original_application_name.ini";
    private final static String SIGNATURE_INFO_ASSET_PATH = "assets/original_signature_info.ini";
    private static final String[] APK_LIB_PATH_ARRAY = {
            "lib/armeabi-v7a",
            "lib/armeabi",
            "lib/arm64-v8a",
            "lib/x86",
            "lib/x86_64"
    };

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
            System.out.println();
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

        File finalApk = new File(String.format("%s-%s-unsigned.apk", getBaseName(srcApkFile.getAbsolutePath()),
                Constants.CONFIG_NAME_SIGBYPASSLV + sigbypassLevel));
        FileUtils.copyFile(srcApkFile, finalApk);

        ZFile zFile = new ZFile(finalApk);

        String currentDir = new File(".").getAbsolutePath();
        System.out.println("currentDir: " + currentDir);
        System.out.println("apkPath: " + apkPath);

        if (outputPath == null || outputPath.length() == 0) {
            String sig = Constants.CONFIG_NAME_SIGBYPASSLV + sigbypassLevel;
            outputPath = String.format("%s-%s-xposed-signed.apk", getBaseName(apkPath), sig);
        }

        File outputFile = new File(outputPath);
        if (outputFile.exists() && !forceOverwrite) {
            System.err.println(outputPath + " exists, use --force to overwrite");
            usage();
            return;
        }

        String outputApkFileParentPath = outputFile.getParent();
        if (outputApkFileParentPath == null) {
            String absPath = outputFile.getAbsolutePath();
            int index = absPath.lastIndexOf(File.separatorChar);
            outputApkFileParentPath = absPath.substring(0, index);
        }

        String apkFileName = getBaseName(srcApkFile);

        String tempFilePath = outputApkFileParentPath + File.separator +
                currentTimeStr() + "-tmp" + File.separator;

        unzipApkFilePath = tempFilePath + apkFileName + "-" + UNZIP_APK_FILE_NAME + File.separator;

        // save the apk original signature info, to support crach signature.
        String originalSignature = ApkSignatureHelper.getApkSignInfo(apkPath);
        if (originalSignature == null || originalSignature.isEmpty()) {
            throw new IllegalStateException("get original signature failed");
        }
        File osi = new File((unzipApkFilePath + SIGNATURE_INFO_ASSET_PATH).replace("/", File.separator));
        FileUtils.write(osi, originalSignature, Charset.defaultCharset());
        zFile.add(SIGNATURE_INFO_ASSET_PATH, new FileInputStream(osi));

        // get the dex count in the apk zip file
        dexFileCount = findDexFileCount(zFile);

        System.out.println("dexFileCount: " + dexFileCount);

        // copy out manifest file from zlib
        int copySize = IOUtils.copy(zFile.get("AndroidManifest.xml").open(), new FileOutputStream(unzipApkFilePath + "AndroidManifest.xml.bak"));
        if (copySize <= 0) {
            throw new IllegalStateException("wtf");
        }
        String manifestFilePath = unzipApkFilePath + "AndroidManifest.xml.bak";

        // parse the app main application full name from the manifest file
        ManifestParser.Pair pair = ManifestParser.parseManifestFile(manifestFilePath);
        String applicationName = null;
        if (pair != null && pair.applicationName != null) {
            applicationName = pair.applicationName;
        }

        System.out.println("original application name: " + applicationName);

        // modify manifest
        modifyManifestFile(manifestFilePath, new File(unzipApkFilePath, "AndroidManifest.xml").getPath());

        // save original main application name to asset file even its empty
        File oan = new File((unzipApkFilePath + APPLICATION_NAME_ASSET_PATH).replace("/", File.separator));
        FileUtils.write(oan, applicationName, Charset.defaultCharset());
        zFile.add(APPLICATION_NAME_ASSET_PATH, new FileInputStream(oan));

        // copy so and dex files into the unzipped apk
        Set<String> apkArchs = new HashSet<>();

        System.out.println("search target apk library arch..");
        for (StoredEntry storedEntry : zFile.entries()) {
            for (String arch : APK_LIB_PATH_ARRAY) {
                if (storedEntry.getCentralDirectoryHeader().getName().startsWith(arch)) {
                    apkArchs.add(arch);
                }
            }
        }

        if (apkArchs.isEmpty()) {
            apkArchs.add(APK_LIB_PATH_ARRAY[0]);
        }

        for (String arch : apkArchs) {
            // lib/armeabi-v7a -> armeabi-v7a
            String justArch = arch.substring(arch.indexOf('/'));
            File sod = new File("list-so", justArch);
            File[] files = sod.listFiles();
            if (files == null) {
                System.out.println("Warning: Nothing so file has been copied in " + sod.getPath());
                continue;
            }
            for (File file : files) {
                zFile.add(arch + "/" + file.getName(), new FileInputStream(file));
                System.out.println("add " + file.getPath());
            }
        }

        // copy all dex files in list-dex
        File[] files = new File("list-dex").listFiles();
        if (files == null || files.length == 0) {
            System.out.println("Warning: Nothing dex file has been copied");
            return;
        }
        for (File file : files) {
            String copiedDexFileName = "classes" + (dexFileCount + 1) + ".dex";
            zFile.add(copiedDexFileName, new FileInputStream(file));
            dexFileCount++;
        }

        // copy origin apk to assets
        // convenient to bypass some check like CRC
        if (sigbypassLevel >= Constants.SIGBYPASS_LV_PM_OPENAT) {
            zFile.add("assets/origin_apk.bin", new FileInputStream(srcApkFile));
        }

        File[] listAssets = new File("list-assets").listFiles();
        if (listAssets == null || listAssets.length == 0) {
            System.out.println("Warning: No assets file copyied");
        }
        else {
            for (File f : listAssets) {
                if (f.isDirectory()) {
                    throw new IllegalStateException("unsupport directory in assets");
                }
                zFile.add("assets/" + f.getName(), new FileInputStream(f));
            }
        }

        // save lspatch config to asset..
        File sl = new File(unzipApkFilePath, "tmp");
        FileUtils.write(sl, "42", Charset.defaultCharset());
        zFile.add("assets/" + Constants.CONFIG_NAME_SIGBYPASSLV + sigbypassLevel, new FileInputStream(sl));

        zFile.update();
        zFile.close();

        new BuildAndSignApkTask(true, unzipApkFilePath, finalApk.getAbsolutePath(), outputPath).run();
        System.out.println("Output APK: " + outputPath);
    }

    private void modifyManifestFile(String filePath, String dstFilePath) {
        ModificationProperty property = new ModificationProperty();

        if (debuggableFlag >= 0) {
            property.addApplicationAttribute(new AttributeItem(NodeValue.Application.DEBUGGABLE, debuggableFlag != 0));
        }

        property.addApplicationAttribute(new AttributeItem("extractNativeLibs", true));
        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.NAME, proxyName));

        FileProcesser.processManifestFile(filePath, dstFilePath, property);
    }

    private int findDexFileCount(ZFile zFile) {
        for (int i = 2; i < 30; i++) {
            if (zFile.get("classes" + i + ".dex") == null)
                return i - 1;
        }
        throw new IllegalStateException("wtf");
    }

    // Use the current timestamp as the name of the build file
    @SuppressWarnings("SimpleDateFormat")
    private String currentTimeStr() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        return df.format(new Date());
    }
}