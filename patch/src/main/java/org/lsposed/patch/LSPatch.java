package org.lsposed.patch;

import com.android.apksigner.ApkSignerTool;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.wind.meditor.core.ManifestEditor;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.patch.util.ApkSignatureHelper;
import org.lsposed.patch.util.ManifestParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LSPatch {

    static class PatchError extends Error {
        PatchError(String message) {
            super(message);
        }
    }

    @Parameter(description = "apks")
    private ArrayList<String> apkPaths = new ArrayList<>();

    @Parameter(names = {"-h", "--help"}, help = true, order = 0, description = "Print this message")
    private boolean help = false;

    @Parameter(names = {"-o", "--output"}, description = "Output directory")
    private String outputPath = ".";

    @Parameter(names = {"-f", "--force"}, description = "Force overwrite exists output file")
    private boolean forceOverwrite = false;

    @Parameter(names = {"-p", "--proxyname"}, description = "Special proxy app name with full dot path")
    private String proxyName = "org.lsposed.lspatch.appstub.LSPApplicationStub";

    @Parameter(names = {"-d", "--debuggable"}, description = "Set app to be debuggable")
    private boolean debuggableFlag = false;

    @Parameter(names = {"-l", "--sigbypasslv"}, description = "Signature bypass level. 0 (disable), 1 (pm), 2 (pm+openat). default 0")
    private int sigbypassLevel = 0;

    @Parameter(names = {"--v1"}, arity = 1, description = "Sign with v1 signature")
    private boolean v1 = true;

    @Parameter(names = {"--v2"}, arity = 1, description = "Sign with v2 signature")
    private boolean v2 = true;

    @Parameter(names = {"--v3"}, arity = 1, description = "Sign with v3 signature")
    private boolean v3 = true;

    @Parameter(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose = false;

    private int dexFileCount = 0;

    private static final String APPLICATION_NAME_ASSET_PATH = "assets/original_application_name.ini";
    private static final String SIGNATURE_INFO_ASSET_PATH = "assets/original_signature_info.ini";
    private static final String ORIGIN_APK_ASSET_PATH = "assets/origin_apk.bin";
    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final HashSet<String> APK_LIB_PATH_ARRAY = new HashSet<>(Arrays.asList(
//            "armeabi",
            "armeabi-v7a",
            "arm64-v8a",
            "x86",
            "x86_64"
    ));

    private static JCommander jCommander;

    public static void main(String... args) throws IOException {
        LSPatch lsPatch = new LSPatch();
        jCommander = JCommander.newBuilder()
                .addObject(lsPatch)
                .build();
        jCommander.parse(args);
        try {
            lsPatch.doCommandLine();
        } catch (PatchError e) {
            System.err.println(e.getMessage());
        }
    }

    public void doCommandLine() throws PatchError, IOException {
        if (help) {
            jCommander.usage();
            return;
        }
        if (apkPaths == null || apkPaths.isEmpty()) {
            jCommander.usage();
            return;
        }

        for (var apk : apkPaths) {
            File srcApkFile = new File(apk).getAbsoluteFile();

            String apkFileName = srcApkFile.getName();

            var outputDir = new File(outputPath);
            outputDir.mkdirs();

            File outputFile = new File(outputDir, String.format("%s-lv%s-xposed-signed.apk", FilenameUtils.getBaseName(apkFileName), sigbypassLevel)).getAbsoluteFile();

            if (outputFile.exists() && !forceOverwrite)
                throw new PatchError(outputPath + " exists. Use --force to overwrite");
            System.out.println("Processing " + srcApkFile + " -> " + outputFile);
            patch(srcApkFile, outputFile);
        }
    }

    public void patch(File srcApkFile, File outputFile) throws PatchError, IOException {
        if (!srcApkFile.exists())
            throw new PatchError("The source apk file does not exit. Please provide a correct path.");

        var workingDir = Files.createTempDirectory("LSPatch").toFile();
        try {
            File tmpApk = new File(workingDir, String.format("%s-%s-unsigned.apk", srcApkFile.getName(),
                    Constants.CONFIG_NAME_SIGBYPASSLV + sigbypassLevel));
            if (verbose) {
                System.out.println("work dir: " + workingDir);
                System.out.println("apk path: " + srcApkFile);
            }

            System.out.println("Copying to tmp apk...");

            FileUtils.copyFile(srcApkFile, tmpApk);

            System.out.println("Parsing original apk...");
            ZFile zFile = ZFile.openReadWrite(tmpApk);

            if (sigbypassLevel > 0) {
                // save the apk original signature info, to support crack signature.
                String originalSignature = ApkSignatureHelper.getApkSignInfo(srcApkFile.getAbsolutePath());
                if (originalSignature == null || originalSignature.isEmpty()) {
                    throw new PatchError("get original signature failed");
                }
                if (verbose)
                    System.out.println("Original signature\n" + originalSignature);
                try (var is = new ByteArrayInputStream(originalSignature.getBytes(StandardCharsets.UTF_8))) {
                    zFile.add(SIGNATURE_INFO_ASSET_PATH, is);
                } catch (Throwable e) {
                    throw new PatchError("Error when saving signature: " + e);
                }
            }

            // get the dex count in the apk zip file
            dexFileCount = findDexFileCount(zFile);

            if (verbose)
                System.out.println("dexFileCount: " + dexFileCount);

            // copy out manifest file from zlib
            var manifestEntry = zFile.get(ANDROID_MANIFEST_XML);
            if (manifestEntry == null)
                throw new PatchError("Provided file is not a valid apk");

            // parse the app main application full name from the manifest file
            ManifestParser.Pair pair = ManifestParser.parseManifestFile(manifestEntry.open());
            if (pair == null)
                throw new PatchError("Failed to parse AndroidManifest.xml");
            String applicationName = pair.applicationName == null ? "" : pair.applicationName;

            if (verbose)
                System.out.println("original application name: " + applicationName);

            System.out.println("Patching apk...");
            // modify manifest
            try (var is = new ByteArrayInputStream(modifyManifestFile(manifestEntry.open()))) {
                zFile.add(APPLICATION_NAME_ASSET_PATH, is);
            } catch (Throwable e) {
                throw new PatchError("Error when modifying manifest: " + e);
            }

            // save original main application name to asset file even its empty
            try (var is = new ByteArrayInputStream(applicationName.getBytes(StandardCharsets.UTF_8))) {
                zFile.add(APPLICATION_NAME_ASSET_PATH, is);
            } catch (Throwable e) {
                throw new PatchError("Error when saving signature: " + e);
            }

            // copy so and dex files into the unzipped apk
            Set<String> apkArchs = new HashSet<>();

            if (verbose)
                System.out.println("Search target apk library arch..");
            for (StoredEntry storedEntry : zFile.entries()) {
                var name = storedEntry.getCentralDirectoryHeader().getName();
                if (name.startsWith("lib/")) {
                    var arch = name.substring(4, name.indexOf('/', 5));
                    apkArchs.add(arch);
                }
            }

            if (apkArchs.isEmpty()) {
                apkArchs.addAll(APK_LIB_PATH_ARRAY);
            }

            apkArchs.removeIf((arch) -> {
                if (!APK_LIB_PATH_ARRAY.contains(arch)) {
                    System.err.println("Warning: unsupported arch " + arch + ". Skipping...");
                    return true;
                }
                return false;
            });
            if (verbose)
                System.out.println("Adding native lib..");

            for (String arch : apkArchs) {
                // lib/armeabi-v7a -> armeabi-v7a
                String entryName = "lib/" + arch + "/liblspd.so";
                try (var is = getClass().getClassLoader().getResourceAsStream("assets/so/" + arch + "/liblspd.so")) {
                    zFile.add(entryName, is);
                } catch (Throwable e) {
                    throw new PatchError("Error when adding native lib: " + e);
                }
                if (verbose)
                    System.out.println("added " + entryName);
            }

            if (verbose)
                System.out.println("Adding dex..");

            try (var is = getClass().getClassLoader().getResourceAsStream("assets/dex/loader.dex")) {
                String copiedDexFileName = "classes" + (dexFileCount + 1) + ".dex";
                zFile.add(copiedDexFileName, is);
                dexFileCount++;
            } catch (Throwable e) {
                throw new PatchError("Error when add dex: " + e);
            }

            // copy origin apk to assets
            // convenient to bypass some check like CRC
            if (sigbypassLevel >= Constants.SIGBYPASS_LV_PM_OPENAT) {
                zFile.add(ORIGIN_APK_ASSET_PATH, new FileInputStream(srcApkFile));
            }

            try (var is = getClass().getClassLoader().getResourceAsStream("assets/dex/lsp.dex")) {
                zFile.add("assets/lsp", is);
            } catch (Throwable e) {
                throw new PatchError("Error when add assets: " + e);
            }

            // save lspatch config to asset..
            try (var is = new ByteArrayInputStream("42".getBytes(StandardCharsets.UTF_8))) {
                zFile.add("assets/" + Constants.CONFIG_NAME_SIGBYPASSLV + sigbypassLevel, is);
            } catch (Throwable e) {
                throw new PatchError("Error when saving signature: " + e);
            }

            zFile.update();
            zFile.close();

            System.out.println("Signing apk...");
            signApkUsingAndroidApksigner(workingDir, tmpApk, outputFile);

            System.out.println("Done. Output APK: " + outputFile.getAbsolutePath());
        } finally {
            try {
                FileUtils.deleteDirectory(workingDir);
            } catch (Throwable ignored) {
            }
        }
    }

    private byte[] modifyManifestFile(InputStream is) {
        ModificationProperty property = new ModificationProperty();

        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.DEBUGGABLE, debuggableFlag));
        property.addApplicationAttribute(new AttributeItem("extractNativeLibs", true));
        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.NAME, proxyName));

        var os = new ByteArrayOutputStream();
        (new ManifestEditor(is, os, property)).processManifest();
        return os.toByteArray();
    }

    private int findDexFileCount(ZFile zFile) {
        for (int i = 2; ; i++) {
            if (zFile.get("classes" + i + ".dex") == null)
                return i - 1;
        }
    }

    private void signApkUsingAndroidApksigner(File workingDir, File apkPath, File outputPath) throws PatchError, IOException {
        ArrayList<String> commandList = new ArrayList<>();

        var keyStoreFile = new File(workingDir, "keystore");

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("assets/keystore");
             FileOutputStream os = new FileOutputStream(keyStoreFile)) {
            if (is == null)
                throw new PatchError("Fail to save keystore file");
            IOUtils.copy(is, os);
        }

        commandList.add("sign");
        commandList.add("--ks");
        commandList.add(keyStoreFile.getAbsolutePath());
        commandList.add("--ks-key-alias");
        commandList.add("key0");
        commandList.add("--ks-pass");
        commandList.add("pass:" + 123456);
        commandList.add("--key-pass");
        commandList.add("pass:" + 123456);
        commandList.add("--out");
        commandList.add(outputPath.getAbsolutePath());
        commandList.add("--v1-signing-enabled");
        commandList.add(Boolean.toString(v1));
        commandList.add("--v2-signing-enabled");   // v2签名不兼容android 6
        commandList.add(Boolean.toString(v2));
        commandList.add("--v3-signing-enabled");   // v3签名不兼容android 6
        commandList.add(Boolean.toString(v3));
        commandList.add(apkPath.getAbsolutePath());

        try {
            ApkSignerTool.main(commandList.toArray(new String[0]));
        } catch (Exception e) {
            throw new PatchError("Failed to sign apk: " + e.getMessage());
        }
    }
}
