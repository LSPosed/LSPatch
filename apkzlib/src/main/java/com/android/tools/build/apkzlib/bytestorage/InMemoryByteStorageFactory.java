package com.android.tools.build.apkzlib.bytestorage;

import java.io.IOException;

/**
 * {@link ByteStorageFactory} that creates {@link ByteStorage} instances that keep all data in
 * memory.
 */
public class InMemoryByteStorageFactory implements ByteStorageFactory {

  @Override
  public ByteStorage create() throws IOException {
    return new InMemoryByteStorage();
  }
}
