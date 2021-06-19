package org.lsposed.patch.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import wind.android.content.res.AXmlResourceParser;
import wind.v1.XmlPullParser;
import wind.v1.XmlPullParserException;

/**
 * Created by Wind
 */
public class ManifestParser {

    public static Pair parseManifestFile(InputStream is) throws IOException {
        AXmlResourceParser parser = new AXmlResourceParser();
        String packageName = null;
        String applicationName = null;
        try {
            parser.open(is);

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
            return null;
        }
        return new Pair(packageName, applicationName);
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
        public String applicationName;

        public Pair(String packageName, String applicationName) {
            this.packageName = packageName;
            this.applicationName = applicationName;
        }
    }

}
