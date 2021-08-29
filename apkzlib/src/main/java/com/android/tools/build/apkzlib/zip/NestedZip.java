package com.android.tools.build.apkzlib.zip;

import com.google.common.collect.Maps;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class NestedZip extends ZFile {
    final Map<StoredEntry, String> links;
    final ZFile target;
    final StoredEntry entry;

    public NestedZip(String name, ZFile target, File src, boolean mayCompress) throws IOException {
        super(src, new ZFileOptions(), true);
        this.target = target;
        this.entry = target.add(name, directOpen(0, directSize()), mayCompress);
        this.links = Maps.newHashMap();
    }

    /**
     * @return true if lfh is consistent with cdh otherwise inconsistent
     */
    public boolean addFileLink(String srcName, String dstName) throws IOException {
        var srcEntry = get(srcName);
        if (entry == null)
            throw new IOException("Entry " + srcEntry + " does not exist in nested zip");
        var offset = srcEntry.getCentralDirectoryHeader().getOffset() + srcEntry.getLocalHeaderSize();
        if (srcName.equals(dstName)) {
            target.addNestedLink(srcName, entry, srcEntry, srcEntry.getCentralDirectoryHeader().getOffset(), true);
            return true;
        } else if (offset < MAX_LOCAL_EXTRA_FIELD_CONTENTS_SIZE) {
            target.addNestedLink(srcName, entry, srcEntry, offset, false);
            return true;
        }
        return false;
    }

    public Map<StoredEntry, String> getLinks() {
        return links;
    }

    public ZFile getTarget() {
        return target;
    }

    public StoredEntry getEntry() {
        return entry;
    }
}
