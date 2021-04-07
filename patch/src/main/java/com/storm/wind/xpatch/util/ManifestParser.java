package com.storm.wind.xpatch.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import wind.android.content.res.AXmlResourceParser;
import wind.v1.XmlPullParser;
import wind.v1.XmlPullParserException;

/**
 * Created by Wind
 */
public class ManifestParser {

    /**
     * Get the package name and the main application name from the manifest file
     * */
    public static Pair parseManifestFile(String filePath) {
        AXmlResourceParser parser = new AXmlResourceParser();
        File file = new File(filePath);
        String packageName = null;
        String applicationName = null;
        if (!file.exists()) {
            System.out.println(" manifest file not exist!!! filePath -> " + filePath);
            return null;
        }
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);

            parser.open(inputStream);

            while (true) {
                int type = parser.next();
                if (type == XmlPullParser.END_DOCUMENT) {
                    break;
                }
                if (type == XmlPullParser.START_TAG) {
                    int attrCount = parser.getAttributeCount();
                    for (int i = 0; i < attrCount; i++) {
                        String attrName = parser.getAttributeName(i);

                        String name = parser.getName();

                        if ("manifest".equals(name)) {
                            if ("package".equals(attrName)) {
                                packageName = parser.getAttributeValue(i);
                            }
                        }

                        if ("application".equals(name)) {
                            if ("name".equals(attrName)) {
                                applicationName = parser.getAttributeValue(i);
                            }
                        }

                        if (packageName != null && packageName.length() > 0 && applicationName != null && applicationName.length() > 0) {
                            return new Pair(packageName, applicationName);
                        }
                    }
                } else if (type == XmlPullParser.END_TAG) {
                    // ignored
                }
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            System.out.println("parseManifestFile failed, reason --> " + e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return new Pair(packageName, applicationName);
    }

    public static class Pair {
        public String packageName;
        public String applicationName;

        public Pair(String packageName, String applicationName) {
            this.packageName = packageName;
            this.applicationName = applicationName;
        }
    }

}
