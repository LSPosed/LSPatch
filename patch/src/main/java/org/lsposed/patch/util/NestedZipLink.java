package org.lsposed.patch.util;

import com.android.tools.build.apkzlib.utils.IOExceptionRunnable;
import com.android.tools.build.apkzlib.zip.CentralDirectoryHeader;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
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

        Field field_offset = CentralDirectoryHeader.class.getDeclaredField("offset");
        field_offset.setAccessible(true);

        Method makeFree = Class.forName("com.android.tools.build.apkzlib.zip.FileUseMapEntry")
                .getDeclaredMethod("makeUsed", long.class, long.class, Object.class);
        makeFree.setAccessible(true);

        Method computeCentralDirectory = ZFile.class.getDeclaredMethod("computeCentralDirectory");
        computeCentralDirectory.setAccessible(true);

        Method computeEocd = ZFile.class.getDeclaredMethod("computeEocd");
        computeEocd.setAccessible(true);

        var entries = (Map<String, Object>) field_entries.get(zFile);
        for (var nestedZip : nestedZips) {
            long nestedZipOffset = nestedZip.entry.getCentralDirectoryHeader().getOffset();
            for (var link : nestedZip.links) {
                var entry = nestedZip.zip.get(link);
                if (entry == null) throw new IOException("Entry " + link + " does not exist in nested zip");
                CentralDirectoryHeader cdh = entry.getCentralDirectoryHeader();
                field_entry_file.set(entry, zFile);
                field_cdh_file.set(cdh, zFile);
                field_offset.set(cdh, nestedZipOffset + cdh.getOffset() + nestedZip.entry.getLocalHeaderSize());
                var newFileUseMapEntry = makeFree.invoke(null, 0, 1, entry);
                entries.put(link, newFileUseMapEntry);
            }
        }
        computeCentralDirectory.invoke(zFile);
        computeEocd.invoke(zFile);
    }
}