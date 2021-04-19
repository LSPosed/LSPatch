package org.lsposed.patch.task;

import org.apache.commons.io.FileUtils;

import java.io.File;

/**
 * Created by xiawanli on 2019/4/6
 */
public class SaveOriginalApplicationNameTask implements Runnable {

    private final String applcationName;
    private final String unzipApkFilePath;
    private String dstFilePath;

    private final String APPLICATION_NAME_ASSET_PATH = "assets/original_application_name.ini";

    public SaveOriginalApplicationNameTask(String applicationName, String unzipApkFilePath) {
        this.applcationName = applicationName;
        this.unzipApkFilePath = unzipApkFilePath;

        this.dstFilePath = (unzipApkFilePath + APPLICATION_NAME_ASSET_PATH).replace("/", File.separator);
    }

    @Override
    public void run() {
        try {
            ensureDstFileCreated();
            FileUtils.write(new File(dstFilePath), applcationName);
        }
        catch (Exception err) {
            // just crash
            // todo: pass result to caller
            throw new IllegalStateException("wtf", err);
        }
    }

    private void ensureDstFileCreated() {
        File dstParentFile = new File(dstFilePath);
        if (!dstParentFile.getParentFile().getParentFile().exists()) {
            if (!dstParentFile.getParentFile().getParentFile().mkdirs()) {
                throw new IllegalStateException("mkdir fail");
            }
        }
        if (!dstParentFile.getParentFile().exists()) {
            if (!dstParentFile.getParentFile().mkdirs()) {
                throw new IllegalStateException("mkdir fail");
            }
        }
    }
}