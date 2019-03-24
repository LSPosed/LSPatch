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
    public static Pair parseManidestFile(String filePath) {
        AXmlResourceParser parser = new AXmlResourceParser();
        File file = new File(filePath);
        String packageName = null;
        String applictionName = null;
        if (!file.exists()) {
            System.out.println(" manifest file not exsit!!! filePath -> " + filePath);
            return null;
        }
        try {
            FileInputStream inputStream = new FileInputStream(file);

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
                                applictionName = parser.getAttributeValue(i);
                            }
                        }

                        if (packageName != null && packageName.length() > 0 && applictionName != null && applictionName.length() > 0) {
                            return new Pair(packageName, applictionName);
                        }
                    }
                } else if (type == XmlPullParser.END_TAG) {
                    // ignored
                }
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            System.out.println("parseManidestFile failed, reason --> " + e.getMessage());
        }
        return new Pair(packageName, applictionName);
    }

    public static class Pair {
        public String packageName;
        public String applictionName;

        public Pair(String packageName, String applictionName) {
            this.packageName = packageName;
            this.applictionName = applictionName;
        }
    }

}
