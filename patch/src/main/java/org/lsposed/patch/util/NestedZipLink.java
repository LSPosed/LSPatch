package org.lsposed.patch.util;

import com.android.tools.build.apkzlib.utils.IOExceptionRunnable;
import com.android.tools.build.apkzlib.zip.CentralDirectoryHeader;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class NestedZipLink extends ZFileExtension {
    public static class NestedZip {
        final Set<String> links;
        final ZFile zip;
        final StoredEntry entry;

        public NestedZip(ZFile zip, StoredEntry entry) {
            this.zip = zip;
            this.entry = entry;
            this.links = new HashSet<>();
        }

        public void addFileLink(String name) {
            links.add(name);
        }
    }

    public final ZFile zFile;

    public final Set<NestedZip> nestedZips = new HashSet<>();

    private boolean written;

    public NestedZipLink(ZFile zFile) {
        this.zFile = zFile;
    }

    @Override
    public IOExceptionRunnable beforeUpdate() {
        written = false;
        return null;
    }

    @Override
    public void entriesWritten() throws IOException {
        if (written) return;
        try {
            Method deleteDirectoryAndEocd = ZFile.class.getDeclaredMethod("deleteDirectoryAndEocd");
            deleteDirectoryAndEocd.setAccessible(true);
            deleteDirectoryAndEocd.invoke(zFile);
            appendEntries();
            Method computeCentralDirectory = ZFile.class.getDeclaredMethod("computeCentralDirectory");
            computeCentralDirectory.setAccessible(true);
            computeCentralDirectory.invoke(zFile);
            Method computeEocd = ZFile.class.getDeclaredMethod("computeEocd");
            computeEocd.setAccessible(true);
            computeEocd.invoke(zFile);
        } catch (Exception e) {
            e.printStackTrace();
            var ex = new IOException("Error when writing link entries");
            ex.addSuppressed(e);
            throw ex;
        }
        written = true;
    }

    private void appendEntries() throws IOException {
        for (var nestedZip : nestedZips) {
            long nestedZipOffset = nestedZip.entry.getCentralDirectoryHeader().getOffset();
            for (var link : nestedZip.links) {
                var entry = nestedZip.zip.get(link);
                if (entry == null) throw new IOException("Entry " + link + " does not exist in nested zip");
                CentralDirectoryHeader cdh = entry.getCentralDirectoryHeader();
                CentralDirectoryHeader clonedCdh;

                try {
                    Method clone = CentralDirectoryHeader.class.getDeclaredMethod("clone");
                    clone.setAccessible(true);
                    clonedCdh = (CentralDirectoryHeader) clone.invoke(cdh);

                    zFile.add(link, new ByteArrayInputStream(new byte[0]));
                    StoredEntry newEntry = zFile.get(link);

                    Field field_file = CentralDirectoryHeader.class.getDeclaredField("file");
                    field_file.setAccessible(true);
                    field_file.set(clonedCdh, zFile);

                    Field field_offset = CentralDirectoryHeader.class.getDeclaredField("offset");
                    field_offset.setAccessible(true);

                    field_offset.set(clonedCdh, nestedZipOffset + cdh.getOffset() + nestedZip.entry.getLocalHeaderSize());

                    Field field_cdh = StoredEntry.class.getDeclaredField("cdh");
                    field_cdh.setAccessible(true);
                    field_cdh.set(newEntry, clonedCdh);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}