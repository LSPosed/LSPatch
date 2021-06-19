package org.lsposed.patch.util;

import java.io.InputStream;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by Wind
 */
public class ApkSignatureHelper {

    private static char[] toChars(byte[] mSignature) {
        byte[] sig = mSignature;
        final int N = sig.length;
        final int N2 = N * 2;
        char[] text = new char[N2];
        for (int j = 0; j < N; j++) {
            byte v = sig[j];
            int d = (v >> 4) & 0xf;
            text[j * 2] = (char) (d >= 10 ? ('a' + d - 10) : ('0' + d));
            d = v & 0xf;
            text[j * 2 + 1] = (char) (d >= 10 ? ('a' + d - 10) : ('0' + d));
        }
        return text;
    }

    private static Certificate[] loadCertificates(JarFile jarFile, JarEntry je, byte[] readBuffer) {
        try {
            InputStream is = jarFile.getInputStream(je);
            while (is.read(readBuffer, 0, readBuffer.length) != -1) {
            }
            is.close();
            return (Certificate[]) (je != null ? je.getCertificates() : null);
        } catch (Exception e) {
        }
        return null;
    }

    public static String getApkSignInfo(String apkFilePath) {
        byte[] readBuffer = new byte[8192];
        Certificate[] certs = null;
        try {
            JarFile jarFile = new JarFile(apkFilePath);
            Enumeration<?> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry je = (JarEntry) entries.nextElement();
                if (je.isDirectory()) {
                    continue;
                }
                if (je.getName().startsWith("META-INF/")) {
                    continue;
                }
                Certificate[] localCerts = loadCertificates(jarFile, je, readBuffer);
                if (certs == null) {
                    certs = localCerts;
                } else {
                    for (int i = 0; i < certs.length; i++) {
                        boolean found = false;
                        for (int j = 0; j < localCerts.length; j++) {
                            if (certs[i] != null && certs[i].equals(localCerts[j])) {
                                found = true;
                                break;
                            }
                        }
                        if (!found || certs.length != localCerts.length) {
                            jarFile.close();
                            return null;
                        }
                    }
                }
            }
            jarFile.close();
            return new String(toChars(certs[0].getEncoded()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
