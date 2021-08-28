package org.lsposed.patch.util;

import com.android.apksig.ApkSignerEngine;
import com.android.apksig.internal.util.Pair;
import com.android.tools.build.apkzlib.sign.SigningExtension;
import com.android.tools.build.apkzlib.utils.IOExceptionRunnable;
import com.android.tools.build.apkzlib.zip.CentralDirectoryHeader;
import com.android.tools.build.apkzlib.zip.EncodeUtils;
import com.android.tools.build.apkzlib.zip.NestedZip;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileExtension;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NestedZipLink extends ZFileExtension {

    public final ZFile zFile;

    public final Set<NestedZip> nestedZips = new HashSet<>();

    private boolean written;

    public final SigningExtension signingExtension;

    public NestedZipLink(ZFile zFile, SigningExtension signingExtension) {
        this.zFile = zFile;
        this.signingExtension = signingExtension;
    }

    @Override
    public IOExceptionRunnable beforeUpdate() {
        return () -> {
            written = false;
            try {

                var signerField = SigningExtension.class.getDeclaredField("signer");
                signerField.setAccessible(true);
                var signer = (ApkSignerEngine) signerField.get(signingExtension);

                for (var nestedZip : nestedZips) {
                    for (var link : nestedZip.getLinks().entrySet()) {
                        var entry = link.getKey();
                        notifySigner(signer, link.getValue(), entry);
                    }
                }

            } catch (Exception e) {
                var ex = new IOException("Error when writing link entries");
                ex.addSuppressed(e);
                throw ex;
            }
        };
    }

    @Override
    public void entriesWritten() throws IOException {
        if (written) return;
        try {
            Method deleteDirectoryAndEocd = ZFile.class.getDeclaredMethod("deleteDirectoryAndEocd");
            deleteDirectoryAndEocd.setAccessible(true);
            deleteDirectoryAndEocd.invoke(zFile);
            appendEntries();
        } catch (Exception e) {
            var ex = new IOException("Error when writing link entries");
            ex.addSuppressed(e);
            throw ex;
        }
        written = true;
    }

    private void appendEntries() throws Exception {
        Field field_entry_file = StoredEntry.class.getDeclaredField("file");
        field_entry_file.setAccessible(true);

        Field field_entries = ZFile.class.getDeclaredField("entries");
        field_entries.setAccessible(true);

        Field field_cdh_file = CentralDirectoryHeader.class.getDeclaredField("file");
        field_cdh_file.setAccessible(true);
        Field field_cdh_encodedFileName = CentralDirectoryHeader.class.getDeclaredField("encodedFileName");
        field_cdh_encodedFileName.setAccessible(true);
        Field field_cdh_offset = CentralDirectoryHeader.class.getDeclaredField("offset");
        field_cdh_offset.setAccessible(true);

        Method makeFree = Class.forName("com.android.tools.build.apkzlib.zip.FileUseMapEntry")
                .getDeclaredMethod("makeUsed", long.class, long.class, Object.class);
        makeFree.setAccessible(true);

        Method computeCentralDirectory = ZFile.class.getDeclaredMethod("computeCentralDirectory");
        computeCentralDirectory.setAccessible(true);

        Method computeEocd = ZFile.class.getDeclaredMethod("computeEocd");
        computeEocd.setAccessible(true);

        var entries = (Map<String, Object>) field_entries.get(zFile);
        for (var nestedZip : nestedZips) {
            long nestedZipOffset = nestedZip.getEntry().getCentralDirectoryHeader().getOffset();
            for (var link : nestedZip.getLinks().entrySet()) {
                var entry = link.getKey();
                CentralDirectoryHeader cdh = entry.getCentralDirectoryHeader();
                field_entry_file.set(entry, zFile);
                field_cdh_file.set(cdh, zFile);
                field_cdh_encodedFileName.set(cdh, encodeFileName(link.getValue()));
                field_cdh_offset.set(cdh, nestedZipOffset + cdh.getOffset() + nestedZip.getEntry().getLocalHeaderSize());
                var newFileUseMapEntry = makeFree.invoke(null, 0, 1, entry);
                entries.put(link.getValue(), newFileUseMapEntry);
            }
        }
        computeCentralDirectory.invoke(zFile);
        computeEocd.invoke(zFile);
    }

    private void notifySigner(ApkSignerEngine signer, String entryName, StoredEntry entry) throws IOException {
        ApkSignerEngine.InspectJarEntryRequest inspectEntryRequest = signer.outputJarEntry(entryName);
        if (inspectEntryRequest != null) {
            try (InputStream inputStream = new BufferedInputStream(entry.open())) {
                int bytesRead;
                byte[] buffer = new byte[65536];
                var dataSink = inspectEntryRequest.getDataSink();
                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    dataSink.consume(buffer, 0, bytesRead);
                }
            }
            inspectEntryRequest.done();
        }
    }

    private byte[] encodeFileName(String name) throws Exception {
        Class<?> GPFlags = Class.forName("com.android.tools.build.apkzlib.zip.GPFlags");
        Method make = GPFlags.getDeclaredMethod("make", boolean.class);
        make.setAccessible(true);
        Method encode = EncodeUtils.class.getDeclaredMethod("encode", String.class, GPFlags);

        boolean encodeWithUtf8 = !EncodeUtils.canAsciiEncode(name);
        var flags = make.invoke(null, encodeWithUtf8);
        return (byte[]) encode.invoke(null, name, flags);
    }

    public void register() throws NoSuchAlgorithmException, IOException {
        zFile.addZFileExtension(this);
        signingExtension.register(zFile);
    }
}
