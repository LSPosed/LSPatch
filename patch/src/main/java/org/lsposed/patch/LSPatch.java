package org.lsposed.patch;

import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.PROXY_APP_COMPONENT_FACTORY;

import com.android.tools.build.apkzlib.sign.SigningExtension;
import com.android.tools.build.apkzlib.sign.SigningOptions;
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

public class LSPatch {

    static class PatchError extends Error {
        public PatchError(String message, Throwable cause) {
            super(message, cause);
        }

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
    private boolean v1 = false;

    @Parameter(names = {"--v2"}, arity = 1, description = "Sign with v2 signature")
    private boolean v2 = true;

    @Parameter(names = {"--v3"}, arity = 1, description = "Sign with v3 signature")
    private boolean v3 = true;

    @Parameter(names = {"--manager"}, description = "Use manager (Cannot work with embedding modules)")
    private boolean useManager = false;

    @Parameter(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose = false;

    @Parameter(names = {"-m", "--embed"}, description = "Embed provided modules to apk")
    private List<String> modules = new ArrayList<>();

    private static final String SIGNATURE_INFO_ASSET_PATH = "assets/original_signature_info.ini";
    private static final String USE_MANAGER_CONTROL_PATH = "assets/use_manager.ini";
    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final HashSet<String> ARCHES = new HashSet<>(Arrays.asList(
            "armeabi-v7a",
            "arm64-v8a",
            "x86",
            "x86_64"
    ));
    private static final HashSet<String> APK_LIB_PATH_ARRAY = new HashSet<>(Arrays.asList(
            "arm",
            "arm64",
            "x86",
            "x86_64"
    ));

    private static final ZFileOptions Z_FILE_OPTIONS = new ZFileOptions().setAlignmentRule(AlignmentRules.compose(
            AlignmentRules.constantForSuffix(".so", 4096),
            AlignmentRules.constantForSuffix(".bin", 4096)
    ));

    private final JCommander jCommander;

    public LSPatch(String... args) {
        jCommander = JCommander.newBuilder()
                .addObject(this)
                .build();
        jCommander.parse(args);
    }

    public static void main(String... args) throws IOException {
        LSPatch lsPatch = new LSPatch(args);
        try {
            lsPatch.doCommandLine();
        } catch (PatchError e) {
            e.printStackTrace(System.err);
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

        if (!modules.isEmpty() && useManager) {
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
        tmpApk.delete();

        if (verbose)
            System.out.println("apk path: " + srcApkFile);

        System.out.println("Parsing original apk...");

        try (ZFile dstZFile = ZFile.openReadWrite(tmpApk, Z_FILE_OPTIONS); var srcZFile = dstZFile.addNestedZip((ignore) -> ORIGINAL_APK_ASSET_PATH, srcApkFile, false)) {

            // sign apk
            System.out.println("Register apk signer...");
            try {
                var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try (var is = getClass().getClassLoader().getResourceAsStream("assets/keystore")) {
                    keyStore.load(is, "123456".toCharArray());
                }
                var entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry("key0", new KeyStore.PasswordProtection("123456".toCharArray()));
                new SigningExtension(SigningOptions.builder()
                        .setMinSdkVersion(27)
                        .setV1SigningEnabled(v1)
                        .setV2SigningEnabled(v2)
                        .setCertificates((X509Certificate[]) entry.getCertificateChain())
                        .setKey(entry.getPrivateKey())
                        .build()).register(dstZFile);
            } catch (Exception e) {
                throw new PatchError("Failed to register signer", e);
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
                    dstZFile.add(SIGNATURE_INFO_ASSET_PATH, is);
                } catch (Throwable e) {
                    throw new PatchError("Error when saving signature", e);
                }
            }

            // copy out manifest file from zlib
            var manifestEntry = srcZFile.get(ANDROID_MANIFEST_XML);
            if (manifestEntry == null)
                throw new PatchError("Provided file is not a valid apk");

            // parse the app main application full name from the manifest file
            ManifestParser.Pair pair = ManifestParser.parseManifestFile(manifestEntry.open());
            if (pair == null)
                throw new PatchError("Failed to parse AndroidManifest.xml");
            String appComponentFactory = pair.appComponentFactory == null ? "" : pair.appComponentFactory;

            if (verbose)
                System.out.println("original appComponentFactory class: " + appComponentFactory);

            System.out.println("Patching apk...");
            // modify manifest
            try (var is = new ByteArrayInputStream(modifyManifestFile(manifestEntry.open()))) {
                dstZFile.add(ANDROID_MANIFEST_XML, is);
            } catch (Throwable e) {
                throw new PatchError("Error when modifying manifest", e);
            }

            // save original appComponentFactory name to asset file even its empty
            try (var is = new ByteArrayInputStream(appComponentFactory.getBytes(StandardCharsets.UTF_8))) {
                dstZFile.add(ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH, is);
            }

            if (verbose)
                System.out.println("Adding native lib..");

            // copy so and dex files into the unzipped apk
            // do not put liblspd.so into apk!lib because x86 native bridge causes crash
            for (String arch : APK_LIB_PATH_ARRAY) {
                String entryName = "assets/lib/lspd/" + arch + "/liblspd.so";
                try (var is = getClass().getClassLoader().getResourceAsStream("assets/so/" + (arch.equals("arm") ? "armeabi-v7a" : (arch.equals("arm64") ? "arm64-v8a" : arch)) + "/liblspd.so")) {
                    dstZFile.add(entryName, is, false); // no compress for so
                } catch (Throwable e) {
                    // More exception info
                    throw new PatchError("Error when adding native lib", e);
                }
                if (verbose)
                    System.out.println("added " + entryName);
            }

            if (verbose)
                System.out.println("Adding dex..");

            try (var is = getClass().getClassLoader().getResourceAsStream("assets/dex/loader.dex")) {
                dstZFile.add("classes.dex", is);
            } catch (Throwable e) {
                throw new PatchError("Error when add dex", e);
            }

            try (var is = getClass().getClassLoader().getResourceAsStream("assets/dex/lsp.dex")) {
                dstZFile.add("assets/lsp", is);
            } catch (Throwable e) {
                throw new PatchError("Error when add assets", e);
            }

            // save lspatch config to asset..
            try (var is = new ByteArrayInputStream("42".getBytes(StandardCharsets.UTF_8))) {
                dstZFile.add("assets/" + Constants.CONFIG_NAME_SIGBYPASSLV + sigbypassLevel, is);
            }
            try (var is = new ByteArrayInputStream(Boolean.toString(useManager).getBytes(StandardCharsets.UTF_8))) {
                dstZFile.add(USE_MANAGER_CONTROL_PATH, is);
            }

            Set<String> apkArchs = new HashSet<>();

            if (verbose)
                System.out.println("Search target apk library arch...");

            for (StoredEntry storedEntry : srcZFile.entries()) {
                var name = storedEntry.getCentralDirectoryHeader().getName();
                if (name.startsWith("lib/") && name.length() >= 5) {
                    var arch = name.substring(4, name.indexOf('/', 5));
                    apkArchs.add(arch);
                }
            }
            if (apkArchs.isEmpty()) apkArchs.addAll(ARCHES);
            apkArchs.removeIf((arch) -> {
                if (!ARCHES.contains(arch) && !arch.equals("armeabi")) {
                    System.err.println("Warning: unsupported arch " + arch + ". Skipping...");
                    return true;
                }
                return false;
            });

            // create zip link
            if (verbose)
                System.out.println("Creating nested apk link...");

            for (var moduleFile : modules) {
                final var moduleManifest = new ManifestParser.Pair[]{null};
                try (var nested = dstZFile.addNestedZip((module) -> {
                    var manifest = module.get(ANDROID_MANIFEST_XML);
                    if (manifest == null) {
                        throw new PatchError(moduleFile + " is not a valid apk file.");
                    }
                    moduleManifest[0] = ManifestParser.parseManifestFile(manifest.open());
                    if (moduleManifest[0] == null) {
                        throw new PatchError(moduleFile + " is not a valid apk file.");
                    }
                    return "assets/modules/" + moduleManifest[0].packageName + ".bin";
                }, new File(moduleFile), false)) {
                    var packageName = moduleManifest[0].packageName;
                    for (var arch : apkArchs) {
                        dstZFile.addLink(nested.getEntry(), "lib/" + arch + "/" + packageName + ".so");
                    }
                }
            }

            for (StoredEntry entry : srcZFile.entries()) {
                String name = entry.getCentralDirectoryHeader().getName();
                if (name.startsWith("classes") && name.endsWith(".dex")) continue;
                if (dstZFile.get(name) != null) continue;
                if (name.equals("AndroidManifest.xml")) continue;
                if (name.startsWith("META-INF") && (name.endsWith(".SF") || name.endsWith(".MF") || name.endsWith(".RSA"))) continue;
                srcZFile.addFileLink(name, name);
            }

            dstZFile.realign();

            System.out.println("Writing apk...");
        } finally {
            try {
                outputFile.delete();
                FileUtils.moveFile(tmpApk, outputFile);
                System.out.println("Done. Output APK: " + outputFile.getAbsolutePath());
            } catch (Throwable e) {
                throw new PatchError("Error writing apk", e);
            }
        }
    }

    private byte[] modifyManifestFile(InputStream is) throws IOException {
        ModificationProperty property = new ModificationProperty();

        if (!modules.isEmpty())
            property.addApplicationAttribute(new AttributeItem("extractNativeLibs", true));
        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.DEBUGGABLE, debuggableFlag));
        property.addApplicationAttribute(new AttributeItem("appComponentFactory", PROXY_APP_COMPONENT_FACTORY));
        // TODO: replace query_all with queries -> manager
        property.addUsesPermission("android.permission.QUERY_ALL_PACKAGES");

        var os = new ByteArrayOutputStream();
        (new ManifestEditor(is, os, property)).processManifest();
        is.close();
        os.flush();
        os.close();
        return os.toByteArray();
    }
}
