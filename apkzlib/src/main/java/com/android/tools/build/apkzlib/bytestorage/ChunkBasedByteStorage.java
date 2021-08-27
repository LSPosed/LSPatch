package com.android.tools.build.apkzlib.bytestorage;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Byte storage that breaks byte sources into smaller byte sources. This storage uses another
 * storage as a delegate and, when a source is requested, it will allocate one or more sources from
 * the delegate to build the requested source.
 */
public class ChunkBasedByteStorage implements ByteStorage {

  /** Size of the default chunk size. */
  private static final long DEFAULT_CHUNK_SIZE_BYTES = 10 * 1024 * 1024;

  /** Maximum size of each chunk. */
  private final long maxChunkSize;

  /** Byte storage where the data is actually stored. */
  private final ByteStorage delegate;

  /**
   * Creates a new storage breaking sources in chunks with the default maximum size and allocating
   * each chunk from {@code delegate}.
   */
  ChunkBasedByteStorage(ByteStorage delegate) {
    this(DEFAULT_CHUNK_SIZE_BYTES, delegate);
  }

  /**
   * Creates a new storage breaking sources in chunks with the maximum of {@code maxChunkSize} and
   * allocating each chunk from {@code delegate}.
   */
  ChunkBasedByteStorage(long maxChunkSize, ByteStorage delegate) {
    this.maxChunkSize = maxChunkSize;
    this.delegate = delegate;
  }

  /** Obtains the byte storage chunks are allocated from. */
  @VisibleForTesting // private otherwise.
  public ByteStorage getDelegate() {
    return delegate;
  }

  @Override
  public CloseableByteSource fromStream(InputStream stream) throws IOException {
    List<CloseableByteSource> sources = new ArrayList<>();
    while (true) {
      LimitedInputStream limitedInput = new LimitedInputStream(stream, maxChunkSize);
      sources.add(delegate.fromStream(limitedInput));
      if (limitedInput.isInputFinished()) {
        break;
      }
    }

    return new ChunkBasedCloseableByteSource(sources);
  }

  @Override
  public CloseableByteSourceFromOutputStreamBuilder makeBuilder() throws IOException {
    return new AbstractCloseableByteSourceFromOutputStreamBuilder() {
      private final List<CloseableByteSource> sources = new ArrayList<>();
      @Nullable private CloseableByteSourceFromOutputStreamBuilder currentBuilder = null;
      private long written = 0;

      @Override
      protected void doWrite(byte[] b, int off, int len) throws IOException {
        int actualOffset = off;
        int remaining = len;

        while (remaining > 0) {
          // Since we're writing data, make sure we have a builder to create the new source.
          if (currentBuilder == null) {
            currentBuilder = delegate.makeBuilder();
            written = 0;
          }

          // See how much we can write without exceeding maxChunkSize in the current builder.
          int maxWrite = (int) Math.min(maxChunkSize - written, remaining);
          currentBuilder.write(b, actualOffset, maxWrite);
          written += maxWrite;

          remaining -= maxWrite;
          actualOffset += maxWrite;

          // If we've reached the end of the chunk, create the source for the part we have and reset
          // to builder so we start a new one if there is more data.
          if (written == maxChunkSize) {
            sources.add(currentBuilder.build());
            currentBuilder = null;
          }
        }
      }

      @Override
      protected CloseableByteSource doBuild() throws IOException {
        // If we were writing a chunk, close it.
        if (currentBuilder != null) {
          sources.add(currentBuilder.build());
          currentBuilder = null;
        }

        return new ChunkBasedCloseableByteSource(sources);
      }
    };
  }

  @Override
  public CloseableByteSource fromSource(ByteSource source) throws IOException {
    List<CloseableByteSource> sources = new ArrayList<>();

    long end = source.size();
    long start = 0;
    while (start < end) {
      long chunkSize = Math.min(end - start, maxChunkSize);
      sources.add(delegate.fromSource(source.slice(start, chunkSize)));
      start += chunkSize;
    }

    return new ChunkBasedCloseableByteSource(sources);
  }

  @Override
  public long getBytesUsed() {
    return delegate.getBytesUsed();
  }

  @Override
  public long getMaxBytesUsed() {
    return delegate.getMaxBytesUsed();
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
