package com.android.tools.build.apkzlib.bytestorage;

import java.io.IOException;

/** Factory that creates {@link ByteStorage}. */
public interface ByteStorageFactory {

  /**
   * Creates a new storage.
   *
   * @return a storage that should be closed when no longer used.
   */
  ByteStorage create() throws IOException;
}
