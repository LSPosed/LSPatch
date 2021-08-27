package com.android.tools.build.apkzlib.bytestorage;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;

/**
 * Byte storage that keeps data in memory up to a certain size. After that, older sources are moved
 * to disk and the newer ones served from memory.
 *
 * <p>Once unloaded to disk, sources are not reloaded into memory as that would be in direct
 * conflict with the filesystem's caching and the costs would probably outweight the benefits.
 *
 * <p>The maximum memory used by storage is actually larger than the maximum provided. It may exceed
 * the limit by the size of one source. That is because sources are always loaded into memory before
 * the storage decides to flush them to disk.
 */
public class OverflowToDiskByteStorage implements ByteStorage {

  /** Size of the default memory cache. */
  private static final long DEFAULT_MEMORY_CACHE_BYTES = 50 * 1024 * 1024;

  /** In-memory storage. */
  private final InMemoryByteStorage memoryStorage;

  /** Disk-based storage. */
  @VisibleForTesting // private otherwise.
  final TemporaryDirectoryStorage diskStorage;

  /** Tracker that keeps all memory sources. */
  private final LruTracker<LruTrackedCloseableByteSource> memorySourcesTracker;

  /** Maximum amount of data to keep in memory. */
  private final long memoryCacheSize;

  /** Maximum amount of data used. */
  private long maxBytesUsed;

  /**
   * Creates a new byte storage with the default memory cache using the provided temporary directory
   * to write data that overflows the memory size.
   *
   * @param temporaryDirectoryFactory the factory used to create a temporary directory where to
   *     overflow to; the created directory will be closed when the {@link
   *     OverflowToDiskByteStorage} object is closed
   * @throws IOException failed to create the temporary directory
   */
  public OverflowToDiskByteStorage(TemporaryDirectoryFactory temporaryDirectoryFactory)
      throws IOException {
    this(DEFAULT_MEMORY_CACHE_BYTES, temporaryDirectoryFactory);
  }

  /**
   * Creates a new byte storage with the given memory cache size using the provided temporary
   * directory to write data that overflows the memory size.
   *
   * @param memoryCacheSize the in-memory cache; a value of {@link 0} will effectively disable
   *     in-memory caching
   * @param temporaryDirectoryFactory the factory used to create a temporary directory where to
   *     overflow to; the created directory will be closed when the {@link
   *     OverflowToDiskByteStorage} object is closed
   * @throws IOException failed to create the temporary directory
   */
  public OverflowToDiskByteStorage(
      long memoryCacheSize, TemporaryDirectoryFactory temporaryDirectoryFactory)
      throws IOException {
    memoryStorage = new InMemoryByteStorage();
    diskStorage = new TemporaryDirectoryStorage(temporaryDirectoryFactory);
    this.memoryCacheSize = memoryCacheSize;
    this.memorySourcesTracker = new LruTracker<>();
  }

  @Override
  public CloseableByteSource fromStream(InputStream stream) throws IOException {
    CloseableByteSource memSource =
        new LruTrackedCloseableByteSource(memoryStorage.fromStream(stream), memorySourcesTracker);
    checkMaxUsage();
    reviewSources();
    return memSource;
  }

  @Override
  public CloseableByteSourceFromOutputStreamBuilder makeBuilder() throws IOException {
    CloseableByteSourceFromOutputStreamBuilder memBuilder = memoryStorage.makeBuilder();
    return new AbstractCloseableByteSourceFromOutputStreamBuilder() {
      @Override
      protected void doWrite(byte[] b, int off, int len) throws IOException {
        memBuilder.write(b, off, len);
      }

      @Override
      protected CloseableByteSource doBuild() throws IOException {
        CloseableByteSource memSource =
            new LruTrackedCloseableByteSource(memBuilder.build(), memorySourcesTracker);
        checkMaxUsage();
        reviewSources();
        return memSource;
      }
    };
  }

  @Override
  public CloseableByteSource fromSource(ByteSource source) throws IOException {
    CloseableByteSource memSource =
        new LruTrackedCloseableByteSource(memoryStorage.fromSource(source), memorySourcesTracker);
    checkMaxUsage();
    reviewSources();
    return memSource;
  }

  @Override
  public synchronized long getBytesUsed() {
    return memoryStorage.getBytesUsed() + diskStorage.getBytesUsed();
  }

  @Override
  public synchronized long getMaxBytesUsed() {
    return maxBytesUsed;
  }

  /** Checks if we have reached a new high of data usage and set it. */
  private synchronized void checkMaxUsage() {
    if (getBytesUsed() > maxBytesUsed) {
      maxBytesUsed = getBytesUsed();
    }
  }

  /** Checks if any of the sources needs to be written to disk or loaded into memory. */
  private synchronized void reviewSources() throws IOException {
    // Move data from memory to disk until we have at most memoryCacheSize bytes in memory.
    while (memoryStorage.getBytesUsed() > memoryCacheSize) {
      LruTrackedCloseableByteSource last = memorySourcesTracker.last();
      if (last != null) {
        LruTrackedCloseableByteSource lastSource = last;
        lastSource.move(diskStorage);
      }
    }
  }

  /** Obtains the number of bytes stored in memory. */
  public long getMemoryBytesUsed() {
    return memoryStorage.getBytesUsed();
  }

  /** Obtains the maximum number of bytes ever stored in memory. */
  public long getMaxMemoryBytesUsed() {
    return memoryStorage.getMaxBytesUsed();
  }

  /** Obtains the number of bytes stored in disk. */
  public long getDiskBytesUsed() {
    return diskStorage.getBytesUsed();
  }

  /** Obtains the maximum number of bytes ever stored in disk. */
  public long getMaxDiskBytesUsed() {
    return diskStorage.getMaxBytesUsed();
  }

  @Override
  public void close() throws IOException {
    memoryStorage.close();
    diskStorage.close();
  }
}
