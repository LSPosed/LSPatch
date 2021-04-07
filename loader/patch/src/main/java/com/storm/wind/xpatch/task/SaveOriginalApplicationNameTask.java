package com.storm.wind.xpatch.task;

import com.storm.wind.xpatch.util.FileUtils;

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
        ensureDstFileCreated();
        FileUtils.writeFile(dstFilePath, applcationName);
    }

    private void ensureDstFileCreated() {
        File dstParentFile = new File(dstFilePath);
        if (!dstParentFile.getParentFile().getParentFile().exists()) {
           if(!dstParentFile.getParentFile().getParentFile().mkdirs()){
               throw new IllegalStateException("mkdir fail");
           }
        }
        if (!dstParentFile.getParentFile().exists()) {
            if(!dstParentFile.getParentFile().mkdirs()){
                throw new IllegalStateException("mkdir fail");
            }
        }
    }
}