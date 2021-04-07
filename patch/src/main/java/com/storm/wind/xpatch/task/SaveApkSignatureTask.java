package com.storm.wind.xpatch.task;

import com.storm.wind.xpatch.util.ApkSignatureHelper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Created by Wind
 */
public class SaveApkSignatureTask implements Runnable {

    private String apkPath;
    private String dstFilePath;

    private final static String SIGNATURE_INFO_ASSET_PATH = "assets/original_signature_info.ini";

    public SaveApkSignatureTask(String apkPath, String unzipApkFilePath) {
        this.apkPath = apkPath;
        this.dstFilePath = (unzipApkFilePath + SIGNATURE_INFO_ASSET_PATH).replace("/", File.separator);
    }

    @Override
    public void run() {
        // First,  get the original signature
        String originalSignature = ApkSignatureHelper.getApkSignInfo(apkPath);
        if (originalSignature == null || originalSignature.isEmpty()) {
            System.out.println("Get original signature failed");
            return;
        }

        // Then, save the signature chars to the asset file
        File file = new File(dstFilePath);
        try {
            FileUtils.write(file, originalSignature, StandardCharsets.UTF_8);
        }
        catch (Exception err) {
            // just crash now
            // todo: pass result to caller
            throw new IllegalStateException("wtf", err);
        }
    }
}
