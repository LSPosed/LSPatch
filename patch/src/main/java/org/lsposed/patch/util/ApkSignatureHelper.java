package org.lsposed.patch.util;

import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by Wind
 */
public class ApkSignatureHelper {
    private static final byte[] APK_V2_MAGIC = {'A', 'P', 'K', ' ', 'S', 'i', 'g', ' ',
            'B', 'l', 'o', 'c', 'k', ' ', '4', '2'};

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
        try {
            return getApkSignV2(apkFilePath);
        } catch (Exception e) {
            return getApkSignV1(apkFilePath);
        }
    }

    public static String getApkSignV1(String apkFilePath) {
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
            return certs != null ? new String(toChars(certs[0].getEncoded())) : null;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String getApkSignV2(String apkFilePath) throws IOException {
        try (RandomAccessFile apk = new RandomAccessFile(apkFilePath, "r")) {
            ByteBuffer buffer = ByteBuffer.allocate(0x10);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            apk.seek(apk.length() - 0x6);
            apk.readFully(buffer.array(), 0x0, 0x6);
            int offset = buffer.getInt();
            if (buffer.getShort() != 0) {
                throw new UnsupportedEncodingException("no zip");
            }

            apk.seek(offset - 0x10);
            apk.readFully(buffer.array(), 0x0, 0x10);

            if (!Arrays.equals(buffer.array(), APK_V2_MAGIC)) {
                throw new UnsupportedEncodingException("no apk v2");
            }

            // Read and compare size fields
            apk.seek(offset - 0x18);
            apk.readFully(buffer.array(), 0x0, 0x8);
            buffer.rewind();
            int size = (int) buffer.getLong();

            ByteBuffer block = ByteBuffer.allocate(size + 0x8);
            block.order(ByteOrder.LITTLE_ENDIAN);
            apk.seek(offset - block.capacity());
            apk.readFully(block.array(), 0x0, block.capacity());

            if (size != block.getLong()) {
                throw new UnsupportedEncodingException("no apk v2");
            }

            while (block.remaining() > 24) {
                size = (int) block.getLong();
                if (block.getInt() == 0x7109871a) {
                    // signer-sequence length, signer length, signed data length
                    block.position(block.position() + 12);
                    size = block.getInt(); // digests-sequence length

                    // digests, certificates length
                    block.position(block.position() + size + 0x4);

                    size = block.getInt(); // certificate length
                    break;
                } else {
                    block.position(block.position() + size - 0x4);
                }
            }

            byte[] certificate = new byte[size];
            block.get(certificate);

            return new String(toChars(certificate));
        }
    }
}
