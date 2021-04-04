package com.storm.wind.xpatch.util;

import org.apache.commons.io.FileSystemUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by Wind
 */
public class FileUtils {

    static final int BUFFER = 8192;

    /**
     * 解压文件
     *
     * @param zipPath 要解压的目标文件
     * @param descDir 指定解压目录
     * @return 解压结果：成功，失败
     */
    @SuppressWarnings("rawtypes")
    public static void decompressZip(String zipPath, String descDir) throws IOException {
        File zipFile = new File(zipPath);
        if (!descDir.endsWith(File.separator)) {
            descDir = descDir + File.separator;
        }
        File pathFile = new File(descDir);
        if (!pathFile.exists()) {
            if (!pathFile.mkdirs()) {
                throw new IllegalStateException("mkdir fail " + pathFile.getAbsolutePath());
            }
        }

        try (ZipFile zip = new ZipFile(zipFile, Charset.forName("gbk"))) {
            for (Enumeration entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                String zipEntryName = entry.getName();

                String outPath = (descDir + zipEntryName).replace("/", File.separator);
                File file = new File(outPath);

                if (entry.isDirectory()) {
                    if (!file.mkdirs()) {
                        throw new IllegalStateException("mkdir fail " + file.getAbsolutePath());
                    }
                    continue;
                }

                try (InputStream in = zip.getInputStream(entry)) {
                    if (file.getParentFile() != null && !file.getParentFile().exists()) {
                        if (!file.getParentFile().mkdirs()) {
                            throw new IllegalStateException("mkdir fail " + file.getAbsolutePath());
                        }
                        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                            Runtime.getRuntime().exec("fsutil file setCaseSensitiveInfo " + file.getParentFile().getAbsolutePath());
                            System.out.println("Enable setCaseSensitiveInfo for " + file.getParentFile().getAbsolutePath());
                        }
                    }
                    OutputStream out = new FileOutputStream(outPath);
                    IOUtils.copy(in, out);
                    out.close();
                } catch (Exception err) {
                    throw new IllegalStateException("wtf", err);
                }
            }
        }
    }

    private static InputStream getInputStreamFromFile(String filePath) {
        return FileUtils.class.getClassLoader().getResourceAsStream(filePath);
    }

    // copy an asset file into a path
    public static void copyFileFromJar(String inJarPath, String distPath) {

//        System.out.println("start copyFile  inJarPath =" + inJarPath + "  distPath = " + distPath);
        InputStream inputStream = getInputStreamFromFile(inJarPath);

        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = new BufferedInputStream(inputStream);
            out = new BufferedOutputStream(new FileOutputStream(distPath));

            int len = -1;
            byte[] b = new byte[1024];
            while ((len = in.read(b)) != -1) {
                out.write(b, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(out);
            close(in);
        }
    }

    public static void compressToZip(String srcPath, String dstPath) {
        File srcFile = new File(srcPath);
        File dstFile = new File(dstPath);
        if (!srcFile.exists()) {
            System.out.println(srcPath + " does not exist ！");
            return;
        }

        FileOutputStream out = null;
        ZipOutputStream zipOut = null;
        try {
            out = new FileOutputStream(dstFile);
            CheckedOutputStream cos = new CheckedOutputStream(out, new CRC32());
            zipOut = new ZipOutputStream(cos);
            String baseDir = "";
            compress(srcFile, zipOut, baseDir, true);
        } catch (IOException e) {
            System.out.println(" compress exception = " + e.getMessage());
        } finally {
            try {
                if (zipOut != null) {
                    zipOut.closeEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            close(zipOut);
            close(out);
        }
    }

    private static void compress(File file, ZipOutputStream zipOut, String baseDir, boolean isRootDir) throws IOException {
        if (file.isDirectory()) {
            compressDirectory(file, zipOut, baseDir, isRootDir);
        } else {
            compressFile(file, zipOut, baseDir);
        }
    }

    /**
     * 压缩一个目录
     */
    private static void compressDirectory(File dir, ZipOutputStream zipOut, String baseDir, boolean isRootDir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            String compressBaseDir = "";
            if (!isRootDir) {
                compressBaseDir = baseDir + dir.getName() + "/";
            }
            compress(files[i], zipOut, compressBaseDir, false);
        }
    }

    /**
     * 压缩一个文件
     */
    private static void compressFile(File file, ZipOutputStream zipOut, String baseDir) throws IOException {
        if (!file.exists()) {
            return;
        }

        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            ZipEntry entry = new ZipEntry(baseDir + file.getName());
            zipOut.putNextEntry(entry);
            int count;
            byte data[] = new byte[BUFFER];
            while ((count = bis.read(data, 0, BUFFER)) != -1) {
                zipOut.write(data, 0, count);
            }

        } finally {
            if (null != bis) {
                bis.close();
            }
        }
    }

    public static void writeFile(String filePath, String content) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        if (content == null || content.isEmpty()) {
            return;
        }

        File dstFile = new File(filePath);

        if (!dstFile.getParentFile().exists()) {
            dstFile.getParentFile().mkdirs();
        }

        FileOutputStream outputStream = null;
        BufferedWriter writer = null;
        try {
            outputStream = new FileOutputStream(dstFile);
            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(content);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(outputStream);
            close(writer);
        }
    }

    private static void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

}
