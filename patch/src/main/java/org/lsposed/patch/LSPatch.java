package org.lsposed.patch;

import com.android.apksig.ApkSigner;
import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.wind.meditor.core.ManifestEditor;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.patch.util.ApkSignatureHelper;
import org.lsposed.patch.util.ManifestParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

public class LSPatch {

    static class PatchError extends Error {
        PatchError(String message) {
            super(message);
        }
    }

    @Parameter(description = "apks")
    private List<String> apkPaths = new ArrayList<>();

    @Parameter(names = {"-h", "--help"}, help = true, order = 0, description = "Print this message")
    private boolean help = false;

    @Parameter(names = {"-o", "--output"}, description = "Output directory")
    private String outputPath = ".";

    @Parameter(names = {"-f", "--force"}, description = "Force overwrite exists output file")
    private boolean forceOverwrite = false;

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

    @Parameter(names = {"-m", "--embed"}, description = "Embed provided modules to apk")
    private List<String> modules = new ArrayList<>();

    private static final String PROXY_APP_COMPONENT_FACTORY = "org.lsposed.lspatch.appstub.LSPAppComponentFactoryStub";
    private static final String PROXY_APPLICATION = "org.lsposed.lspatch.appstub.LSPApplicationStub";

    private static final String APP_COMPONENT_FACTORY_ASSET_PATH = "assets/original_app_component_factory.ini";
    private static final String APPLICATION_NAME_ASSET_PATH = "assets/original_application_name.ini";
    private static final String SIGNATURE_INFO_ASSET_PATH = "assets/original_signature_info.ini";
    private static final String ORIGINAL_APK_ASSET_PATH = "assets/origin_apk.bin";
    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String RESOURCES_ARSC = "resources.arsc";
    private static final HashSet<String> APK_LIB_PATH_ARRAY = new HashSet<>(Arrays.asList(
//            "armeabi",
            "armeabi-v7a",
            "arm64-v8a",
            "x86",
            "x86_64"
    ));

    private static JCommander jCommander;
    private boolean hasAppComponentFactory;

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

        File tmpApk = Files.createTempFile(srcApkFile.getName(), "unsigned").toFile();

        if (verbose)
            System.out.println("apk path: " + srcApkFile);

        System.out.println("Copying to tmp apk...");

        FileUtils.copyFile(srcApkFile, tmpApk);

        System.out.println("Parsing original apk...");

        final ZFileOptions zFileOptions = new ZFileOptions();

        final var alignmentRule = AlignmentRules.compose(
                AlignmentRules.constantForSuffix(RESOURCES_ARSC, 4),
                AlignmentRules.constantForSuffix(ORIGINAL_APK_ASSET_PATH, 4096)
        );
        zFileOptions.setAlignmentRule(alignmentRule);
        try (ZFile zFile = ZFile.openReadWrite(tmpApk, zFileOptions)) {
            // copy origin apk to assets
            zFile.add(ORIGINAL_APK_ASSET_PATH, new FileInputStream(srcApkFile), false);

            // remove unnecessary files
            for (StoredEntry storedEntry : zFile.entries()) {
                var name = storedEntry.getCentralDirectoryHeader().getName();
                if (name.endsWith(".dex")) storedEntry.delete();
            }

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

            // copy out manifest file from zlib
            var manifestEntry = zFile.get(ANDROID_MANIFEST_XML);
            if (manifestEntry == null)
                throw new PatchError("Provided file is not a valid apk");

            // parse the app main application full name from the manifest file
            ManifestParser.Triple triple = ManifestParser.parseManifestFile(manifestEntry.open());
            if (triple == null)
                throw new PatchError("Failed to parse AndroidManifest.xml");
            String applicationName = triple.applicationName == null ? "" : triple.applicationName;
            String appComponentFactory = triple.appComponentFactory;
            hasAppComponentFactory = appComponentFactory != null;

            if (verbose) {
                System.out.println("original application name: " + applicationName);
                System.out.println("original appComponentFactory class: " + appComponentFactory);
            }

            System.out.println("Patching apk...");
            // modify manifest
            try (var is = new ByteArrayInputStream(modifyManifestFile(manifestEntry.open()))) {
                zFile.add(ANDROID_MANIFEST_XML, is);
            } catch (Throwable e) {
                throw new PatchError("Error when modifying manifest: " + e);
            }

            // save original appComponentFactory name to asset file even its empty
            if (appComponentFactory != null)
                try (var is = new ByteArrayInputStream(appComponentFactory.getBytes(StandardCharsets.UTF_8))) {
                    zFile.add(APP_COMPONENT_FACTORY_ASSET_PATH, is);
                } catch (Throwable e) {
                    throw new PatchError("Error when saving appComponentFactory class: " + e);
                }

            // save original main application name to asset file even its empty
            try (var is = new ByteArrayInputStream(applicationName.getBytes(StandardCharsets.UTF_8))) {
                zFile.add(APPLICATION_NAME_ASSET_PATH, is);
            } catch (Throwable e) {
                throw new PatchError("Error when saving application name: " + e);
            }

            // copy so and dex files into the unzipped apk
            Set<String> apkArchs = new HashSet<>();

            if (verbose)
                System.out.println("Search target apk library arch..");
            for (StoredEntry storedEntry : zFile.entries()) {
                var name = storedEntry.getCentralDirectoryHeader().getName();
                if (name.startsWith("lib/") && name.length() >= 5) {
                    var arch = name.substring(4, name.indexOf('/', 5));
                    apkArchs.add(arch);
                }
            }

            if (apkArchs.isEmpty()) {
                apkArchs.addAll(APK_LIB_PATH_ARRAY);
            }

            apkArchs.removeIf((arch) -> {
                if (!APK_LIB_PATH_ARRAY.contains(arch) && !arch.equals("armeabi")) {
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
                try (var is = getClass().getClassLoader().getResourceAsStream("assets/so/" + (arch.equals("armeabi") ? "armeabi-v7a" : arch) + "/liblspd.so")) {
                    zFile.add(entryName, is, false); // no compress for so
                } catch (Throwable e) {
                    throw new PatchError("Error when adding native lib: " + e);
                }
                if (verbose)
                    System.out.println("added " + entryName);
            }

            if (verbose)
                System.out.println("Adding dex..");

            try (var is = getClass().getClassLoader().getResourceAsStream("assets/dex/loader.dex")) {
                zFile.add("classes.dex", is);
            } catch (Throwable e) {
                throw new PatchError("Error when add dex: " + e);
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
                throw new PatchError("Error when saving signature bypass level: " + e);
            }

            embedModules(zFile);

            System.out.println("Signing apk...");
            var sign = zFile.get("META-INF/MANIFEST.MF");
            if (sign != null)
                sign.delete();

            zFile.realign();
            zFile.update();

            signApkUsingAndroidApksigner(tmpApk, outputFile);

            System.out.println("Done. Output APK: " + outputFile.getAbsolutePath());
        } finally {
            try {
                tmpApk.delete();
            } catch (Throwable ignored) {
            }
        }
    }

    private void embedModules(ZFile zFile) {
        System.out.println("Embedding modules...");
        for (var module : modules) {
            var file = new File(module);
            if (!file.exists()) {
                System.err.println(file.getAbsolutePath() + " does not exist.");
            }

            System.out.print("Embedding module ");

            ManifestParser.Triple triple = null;
            try (JarFile jar = new JarFile(file)) {
                var manifest = jar.getEntry(ANDROID_MANIFEST_XML);
                if (manifest == null) {
                    System.out.println();
                    System.err.println(file.getAbsolutePath() + " is not a valid apk file.");
                    continue;
                }
                triple = ManifestParser.parseManifestFile(jar.getInputStream(manifest));
                if (triple == null) {
                    System.out.println();
                    System.err.println(file.getAbsolutePath() + " is not a valid apk file.");
                    continue;
                }
                System.out.println(triple.packageName);
            } catch (Throwable e) {
                System.out.println();
                System.err.println(e.getMessage());
            }
            if (triple != null) {
                try (var is = new FileInputStream(file)) {
                    zFile.add("assets/modules/" + triple.packageName, is);
                } catch (Throwable e) {
                    System.err.println("Embed " + triple.packageName + " with error: " + e.getMessage());
                }
            }
        }

    }

    private byte[] modifyManifestFile(InputStream is) throws IOException {
        ModificationProperty property = new ModificationProperty();

        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.DEBUGGABLE, debuggableFlag));
        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.NAME, PROXY_APPLICATION));
        if (hasAppComponentFactory)
            property.addApplicationAttribute(new AttributeItem("appComponentFactory", PROXY_APP_COMPONENT_FACTORY));

        var os = new ByteArrayOutputStream();
        (new ManifestEditor(is, os, property)).processManifest();
        is.close();
        os.flush();
        os.close();
        return os.toByteArray();
    }

    private void signApkUsingAndroidApksigner(File apkPath, File outputPath) throws PatchError {
        try {
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (var is = getClass().getClassLoader().getResourceAsStream("assets/keystore")) {
                keyStore.load(is, "123456".toCharArray());
            }
            var entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry("key0", new KeyStore.PasswordProtection("123456".toCharArray()));

            ApkSigner.SignerConfig signerConfig =
                    new ApkSigner.SignerConfig.Builder(
                            "lspatch", entry.getPrivateKey(), Arrays.asList((X509Certificate[]) entry.getCertificateChain()))
                            .build();
            ApkSigner apkSigner = new ApkSigner.Builder(List.of(signerConfig))
                    .setInputApk(apkPath)
                    .setOutputApk(outputPath)
                    .setOtherSignersSignaturesPreserved(false)
                    .setV1SigningEnabled(v1)
                    .setV2SigningEnabled(v2)
                    .setV3SigningEnabled(v3)
                    .setDebuggableApkPermitted(true)
                    .setSigningCertificateLineage(null)
                    .setMinSdkVersion(27).build();
            apkSigner.sign();
        } catch (Exception e) {
            throw new PatchError("Failed to sign apk: " + e.getMessage());
        }
    }
}
