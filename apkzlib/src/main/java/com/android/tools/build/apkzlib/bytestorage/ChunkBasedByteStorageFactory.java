package com.android.tools.build.apkzlib.bytestorage;

import java.io.IOException;
import javax.annotation.Nullable;

/**
 * {@link ByteStorageFactory} that creates {@link ByteStorage} instances that keep all data in
 * memory.
 */
public class ChunkBasedByteStorageFactory implements ByteStorageFactory {

  /** Factory to create the delegate storages. */
  private final ByteStorageFactory delegate;

  /** Maximum size for chunks, if any. */
  @Nullable private final Long maxChunkSize;

  /** Creates a new factory whose storages are created using delegates from the given factory. */
  public ChunkBasedByteStorageFactory(ByteStorageFactory delegate) {
    this(delegate, /*maxChunkSize=*/ null);
  }

  /**
   * Creates a new factory whose storages use the given maximum chunk size and are created using
   * delegates from the given factory.
   */
  public ChunkBasedByteStorageFactory(ByteStorageFactory delegate, @Nullable Long maxChunkSize) {
    this.delegate = delegate;
    this.maxChunkSize = maxChunkSize;
  }

  @Override
  public ByteStorage create() throws IOException {
    if (maxChunkSize == null) {
      return new ChunkBasedByteStorage(delegate.create());
    } else {
      return new ChunkBasedByteStorage(maxChunkSize, delegate.create());
    }
  }
}
