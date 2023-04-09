package org.lsposed.patch.util;

import com.wind.meditor.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import pxb.android.axml.AxmlParser;

/**
 * Created by Wind
 */
public class ManifestParser {

    public static Pair parseManifestFile(InputStream is) throws IOException {
        AxmlParser parser = new AxmlParser(Utils.getBytesFromInputStream(is));
        String packageName = null;
        String appComponentFactory = null;
        int minSdkVersion = 0;
        try {

            while (true) {
                int type = parser.next();
                if (type == AxmlParser.END_FILE) {
                    break;
                }
                if (type == AxmlParser.START_TAG) {
                    int attrCount = parser.getAttributeCount();
                    for (int i = 0; i < attrCount; i++) {
                        String attrName = parser.getAttrName(i);
                        int attrNameRes = parser.getAttrResId(i);

                        String name = parser.getName();
                        
                        if ("manifest".equals(name)) {
                            if ("package".equals(attrName)) {
                                packageName = parser.getAttrValue(i).toString();
                            }
                        }

                        if ("uses-sdk".equals(name)) {
                            if ("minSdkVersion".equals(attrName)) {
                                minSdkVersion = Integer.parseInt(parser.getAttrValue(i).toString());
                            }
                        }

                        if ("appComponentFactory".equals(attrName) || attrNameRes == 0x0101057a) {
                            appComponentFactory = parser.getAttrValue(i).toString();
                        }

                        if (packageName != null && packageName.length() > 0 &&
                                appComponentFactory != null && appComponentFactory.length() > 0 &&
                                minSdkVersion > 0
                        ) {
                            return new Pair(packageName, appComponentFactory, minSdkVersion);
                        }
                    }
                } else if (type == AxmlParser.END_TAG) {
                    // ignored
                }
            }
        } catch (Exception e) {
            return null;
        }

        return new Pair(packageName, appComponentFactory, minSdkVersion);
    }

    /**
     * Get the package name and the main application name from the manifest file
     */
    public static Pair parseManifestFile(String filePath) throws IOException {
        File file = new File(filePath);
        try (var is = new FileInputStream(file)) {
            return parseManifestFile(is);
        }
    }

    public static class Pair {
        public String packageName;
        public String appComponentFactory;

        public int minSdkVersion;

        public Pair(String packageName, String appComponentFactory, int minSdkVersion) {
            this.packageName = packageName;
            this.appComponentFactory = appComponentFactory;
            this.minSdkVersion = minSdkVersion;
        }
    }

}
