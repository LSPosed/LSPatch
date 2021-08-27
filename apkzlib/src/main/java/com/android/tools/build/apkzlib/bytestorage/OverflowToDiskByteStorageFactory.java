package com.android.tools.build.apkzlib.bytestorage;

import java.io.IOException;
import javax.annotation.Nullable;

/**
 * {@link ByteStorageFactory} that creates instances of {@link ByteStorage} that will keep some data
 * in memory and will overflow to disk when necessary.
 */
public class OverflowToDiskByteStorageFactory implements ByteStorageFactory {

  /** How much data we want to keep in cache? If {@code null} then we want the default value. */
  @Nullable private final Long memoryCacheSizeInBytes;

  /** Factory that creates temporary directories. */
  private final TemporaryDirectoryFactory temporaryDirectoryFactory;

  /**
   * Creates a new factory with an optional in-memory size and a temporary directory for overflow.
   *
   * @param temporaryDirectoryFactory a factory that creates temporary directories that will be used
   *     for overflow of the {@link ByteStorage} instances created by this factory
   */
  public OverflowToDiskByteStorageFactory(TemporaryDirectoryFactory temporaryDirectoryFactory) {
    this(null, temporaryDirectoryFactory);
  }

  /**
   * Creates a new factory with an optional in-memory size and a temporary directory for overflow.
   *
   * @param memoryCacheSizeInBytes how many bytes to keep in memory? If {@code null} then a default
   *     value will be used
   * @param temporaryDirectoryFactory a factory that creates temporary directories that will be used
   *     for overflow of the {@link ByteStorage} instances created by this factory
   */
  public OverflowToDiskByteStorageFactory(
      Long memoryCacheSizeInBytes, TemporaryDirectoryFactory temporaryDirectoryFactory) {
    this.memoryCacheSizeInBytes = memoryCacheSizeInBytes;
    this.temporaryDirectoryFactory = temporaryDirectoryFactory;
  }

  @Override
  public ByteStorage create() throws IOException {
    if (memoryCacheSizeInBytes == null) {
      return new OverflowToDiskByteStorage(temporaryDirectoryFactory);
    } else {
      return new OverflowToDiskByteStorage(memoryCacheSizeInBytes, temporaryDirectoryFactory);
    }
  }
}
