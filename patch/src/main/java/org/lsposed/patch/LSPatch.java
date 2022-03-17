package org.lsposed.patch;

import static org.lsposed.lspatch.share.Constants.CONFIG_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.DEX_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.PROXY_APP_COMPONENT_FACTORY;

import com.android.tools.build.apkzlib.sign.SigningExtension;
import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.wind.meditor.core.ManifestEditor;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.lsposed.lspatch.share.PatchConfig;
import org.lsposed.patch.util.ApkSignatureHelper;
import org.lsposed.patch.util.JavaLogger;
import org.lsposed.patch.util.Logger;
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
import java.util.Objects;
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

    @Parameter(names = {"-r", "--allowdown"}, description = "Allow downgrade installation by overriding versionCode to 1 (In most cases, the app can still get the correct versionCode)")
    private boolean overrideVersionCode = false;

    @Parameter(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose = false;

    @Parameter(names = {"-m", "--embed"}, description = "Embed provided modules to apk")
    private List<String> modules = new ArrayList<>();

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
            AlignmentRules.constantForSuffix(ORIGINAL_APK_ASSET_PATH, 4096)
    ));

    private final JCommander jCommander;

    private final Logger logger;

    public LSPatch(Logger logger, String... args) {
        jCommander = JCommander.newBuilder()
                .addObject(this)
                .build();
        jCommander.parse(args);
        this.logger = logger;
        logger.verbose = verbose;
    }

    public static void main(String... args) throws IOException {
        LSPatch lsPatch = new LSPatch(new JavaLogger(), args);
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

            File outputFile = new File(outputDir, String.format("%s-lv%s-lspatched.apk", FilenameUtils.getBaseName(apkFileName), sigbypassLevel)).getAbsoluteFile();

            if (outputFile.exists() && !forceOverwrite)
                throw new PatchError(outputPath + " exists. Use --force to overwrite");
            logger.i("Processing " + srcApkFile + " -> " + outputFile);

            patch(srcApkFile, outputFile);
        }
    }

    public void patch(File srcApkFile, File outputFile) throws PatchError, IOException {
        if (!srcApkFile.exists())
            throw new PatchError("The source apk file does not exit. Please provide a correct path.");

        outputFile.delete();

        logger.d("apk path: " + srcApkFile);

        logger.i("Parsing original apk...");

        try (var dstZFile = ZFile.openReadWrite(outputFile, Z_FILE_OPTIONS);
             var srcZFile = dstZFile.addNestedZip((ignore) -> ORIGINAL_APK_ASSET_PATH, srcApkFile, false)) {

            // sign apk
            logger.i("Register apk signer...");
            try {
                var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try (var is = getClass().getClassLoader().getResourceAsStream("assets/keystore")) {
                    keyStore.load(is, "123456".toCharArray());
                }
                var entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry("key0", new KeyStore.PasswordProtection("123456".toCharArray()));
                new SigningExtension(SigningOptions.builder()
                        .setMinSdkVersion(28)
                        .setV1SigningEnabled(v1)
                        .setV2SigningEnabled(v2)
                        .setCertificates((X509Certificate[]) entry.getCertificateChain())
                        .setKey(entry.getPrivateKey())
                        .build()).register(dstZFile);
            } catch (Exception e) {
                throw new PatchError("Failed to register signer", e);
            }

            String originalSignature = null;
            if (sigbypassLevel > 0) {
                // save the apk original signature info, to support crack signature.
                originalSignature = ApkSignatureHelper.getApkSignInfo(srcApkFile.getAbsolutePath());
                if (originalSignature == null || originalSignature.isEmpty()) {
                    throw new PatchError("get original signature failed");
                }

                logger.d("Original signature\n" + originalSignature);
            }

            // copy out manifest file from zlib
            var manifestEntry = srcZFile.get(ANDROID_MANIFEST_XML);
            if (manifestEntry == null)
                throw new PatchError("Provided file is not a valid apk");

            // parse the app appComponentFactory full name from the manifest file
            String appComponentFactory;
            try (var is = manifestEntry.open()) {
                var pair = ManifestParser.parseManifestFile(is);
                if (pair == null)
                    throw new PatchError("Failed to parse AndroidManifest.xml");
                appComponentFactory = pair.appComponentFactory;
                logger.d("original appComponentFactory class: " + appComponentFactory);
            }

            logger.i("Patching apk...");
            // modify manifest
            try (var is = new ByteArrayInputStream(modifyManifestFile(manifestEntry.open()))) {
                dstZFile.add(ANDROID_MANIFEST_XML, is);
            } catch (Throwable e) {
                throw new PatchError("Error when modifying manifest", e);
            }

            logger.d("Adding native lib..");

            // copy so and dex files into the unzipped apk
            // do not put liblspd.so into apk!lib because x86 native bridge causes crash
            for (String arch : APK_LIB_PATH_ARRAY) {
                String entryName = "assets/lspatch/so/" + arch + "/liblspatch.so";
                try (var is = getClass().getClassLoader().getResourceAsStream("assets/so/" + (arch.equals("arm") ? "armeabi-v7a" : (arch.equals("arm64") ? "arm64-v8a" : arch)) + "/liblspatch.so")) {
                    dstZFile.add(entryName, is, false); // no compress for so
                } catch (Throwable e) {
                    // More exception info
                    throw new PatchError("Error when adding native lib", e);
                }
                logger.d("added " + entryName);
            }

            logger.d("Adding dex..");

            try (var is = getClass().getClassLoader().getResourceAsStream("assets/dex/loader.dex")) {
                dstZFile.add("classes.dex", is);
            } catch (Throwable e) {
                throw new PatchError("Error when adding dex", e);
            }

            try (var is = getClass().getClassLoader().getResourceAsStream("assets/dex/lsp.dex")) {
                dstZFile.add(DEX_ASSET_PATH, is);
            } catch (Throwable e) {
                throw new PatchError("Error when adding assets", e);
            }

            // save lspatch config to asset..
            var config = new PatchConfig(useManager, sigbypassLevel, originalSignature, appComponentFactory);
            var configBytes = new Gson().toJson(config).getBytes(StandardCharsets.UTF_8);

            try (var is = new ByteArrayInputStream(configBytes)) {
                dstZFile.add(CONFIG_ASSET_PATH, is);
            } catch (Throwable e) {
                throw new PatchError("Error when saving config");
            }

            Set<String> apkArchs = new HashSet<>();

            logger.d("Search target apk library arch...");

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
                    logger.e("Warning: unsupported arch " + arch + ". Skipping...");
                    return true;
                }
                return false;
            });

            embedModules(dstZFile);

            // create zip link
            logger.d("Creating nested apk link...");

            for (StoredEntry entry : srcZFile.entries()) {
                String name = entry.getCentralDirectoryHeader().getName();
                if (name.startsWith("classes") && name.endsWith(".dex")) continue;
                if (dstZFile.get(name) != null) continue;
                if (name.equals("AndroidManifest.xml")) continue;
                if (name.startsWith("META-INF") && (name.endsWith(".SF") || name.endsWith(".MF") || name.endsWith(".RSA"))) continue;
                srcZFile.addFileLink(name, name);
            }

            dstZFile.realign();

            logger.i("Writing apk...");
        }
        logger.i("Done. Output APK: " + outputFile.getAbsolutePath());
    }

    private void embedModules(ZFile zFile) {
        logger.i("Embedding modules...");
        for (var module : modules) {
            File file = new File(module);
            try (var apk = ZFile.openReadOnly(new File(module));
                 var fileIs = new FileInputStream(file);
                 var xmlIs = Objects.requireNonNull(apk.get(ANDROID_MANIFEST_XML)).open()
            ) {
                var manifest = Objects.requireNonNull(ManifestParser.parseManifestFile(xmlIs));
                var packageName = manifest.packageName;
                logger.i("  - " + packageName);
                zFile.add("assets/lspatch/modules/" + packageName + ".bin", fileIs);
            } catch (NullPointerException | IOException e) {
                logger.e(module + " does not exist or is not a valid apk file.");
            }
        }
    }

    private byte[] modifyManifestFile(InputStream is) throws IOException {
        ModificationProperty property = new ModificationProperty();

        if (overrideVersionCode)
            property.addManifestAttribute(new AttributeItem(NodeValue.Manifest.VERSION_CODE, 1));
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
