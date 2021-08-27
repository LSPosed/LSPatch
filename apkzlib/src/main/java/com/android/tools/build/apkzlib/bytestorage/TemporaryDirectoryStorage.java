package com.android.tools.build.apkzlib.bytestorage;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Byte storage that keeps all byte sources as files in a temporary directory. Each data stored is
 * stored as a new file. The file is deleted as soon as the byte source is closed.
 */
public class TemporaryDirectoryStorage implements ByteStorage {

  /** Temporary directory to use. */
  @VisibleForTesting // private otherwise.
  final TemporaryDirectory temporaryDirectory;

  /** Number of bytes currently used. */
  private long bytesUsed;

  /** Maximum number of bytes used. */
  private long maxBytesUsed;

  /**
   * Creates a new storage using the provided temporary directory.
   *
   * @param temporaryDirectoryFactory a factory used to create the directory to use for temporary
   *     files; this directory will be closed when the {@link TemporaryDirectoryStorage} is closed.
   * @throws IOException failed to create the temporary directory
   */
  public TemporaryDirectoryStorage(TemporaryDirectoryFactory temporaryDirectoryFactory)
      throws IOException {
    this.temporaryDirectory = temporaryDirectoryFactory.make();
  }

  @Override
  public CloseableByteSource fromStream(InputStream stream) throws IOException {
    File temporaryFile = temporaryDirectory.newFile();
    try (FileOutputStream output = new FileOutputStream(temporaryFile)) {
      ByteStreams.copy(stream, output);
    }

    long size = temporaryFile.length();
    incrementBytesUsed(size);
    return new TemporaryFileCloseableByteSource(temporaryFile, () -> incrementBytesUsed(-size));
  }

  @Override
  public CloseableByteSourceFromOutputStreamBuilder makeBuilder() throws IOException {
    File temporaryFile = temporaryDirectory.newFile();
    return new AbstractCloseableByteSourceFromOutputStreamBuilder() {
      private final FileOutputStream output = new FileOutputStream(temporaryFile);

      @Override
      protected void doWrite(byte[] b, int off, int len) throws IOException {
        output.write(b, off, len);
        incrementBytesUsed(len);
      }

      @Override
      protected CloseableByteSource doBuild() throws IOException {
        output.close();
        long size = temporaryFile.length();
        return new TemporaryFileCloseableByteSource(temporaryFile, () -> incrementBytesUsed(-size));
      }
    };
  }

  @Override
  public CloseableByteSource fromSource(ByteSource source) throws IOException {
    try (InputStream stream = source.openStream()) {
      return fromStream(stream);
    }
  }

  @Override
  public synchronized long getBytesUsed() {
    return bytesUsed;
  }

  @Override
  public synchronized long getMaxBytesUsed() {
    return maxBytesUsed;
  }

  /** Increments the byte counter by the given amount (decrements if {@code amount} is negative). */
  private synchronized void incrementBytesUsed(long amount) {
    bytesUsed += amount;
    if (bytesUsed > maxBytesUsed) {
      maxBytesUsed = bytesUsed;
    }
  }

  @Override
  public void close() throws IOException {
    temporaryDirectory.close();
  }
}
