package com.android.tools.build.apkzlib.bytestorage;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;

/**
 * Input stream that delegates to another input stream, but can switch transparently the source
 * input stream.
 *
 * <p>Given a set of input streams that return the same data, this input stream will read from one
 * and allow switching to read from other streams continuing from the offset that was initially
 * read. The result is only meaningful if all streams read the same data.
 *
 * <p>This class allows transparently to switch between different implementations of the underlying
 * streams (memory, disk, etc.) while transparently providing data to users. It does not support
 * marking and it is multi-thread safe.
 */
class SwitchableDelegateInputStream extends InputStream {

  /** The input stream that is currently providing data. */
  private InputStream delegate;

  /**
   * Current offset in the input stream. We keep track of this to allow skipping data when switching
   * input streams.
   */
  private long currentOffset;

  /** Have we reached the end of stream? */
  @VisibleForTesting // private otherwise.
  boolean endOfStreamReached;

  /**
   * If a switch has occurred, how many bytes still need to be skipped in the input stream to
   * continue reading from the same position?
   */
  private long needsSkipping;

  SwitchableDelegateInputStream(InputStream delegate) {
    this.delegate = delegate;
    currentOffset = 0;
    endOfStreamReached = false;
    needsSkipping = 0;
  }

  /**
   * Skips data in the input stream if it has been switched and there is data to skip. Will fail if
   * we can't skip all the data.
   */
  private void skipDataIfNeeded() throws IOException {
    while (needsSkipping > 0) {
      long skipped = delegate.skip(needsSkipping);
      if (skipped == 0) {
        throw new IOException("Skipping InputStream after switching failed");
      }

      needsSkipping -= skipped;
    }
  }

  /** Same as {@link #increaseOffset(long)}. */
  private int increaseOffset(int amount) {
    return (int) increaseOffset((long) amount);
  }

  /**
   * Increases the current offset after reading. {@code amount} will indicate how many bytes we have
   * read. It {@code -1} then we know we've reached the end of the stream and {@link
   * #endOfStreamReached} is set to {@code true}.
   */
  private long increaseOffset(long amount) {
    if (amount > 0) {
      currentOffset += amount;
    }

    if (amount == -1) {
      endOfStreamReached = true;
    }

    return amount;
  }

  @Override
  public synchronized int read(byte[] b) throws IOException {
    if (endOfStreamReached) {
      return -1;
    }

    skipDataIfNeeded();
    return increaseOffset(delegate.read(b));
  }

  @Override
  public synchronized int read(byte[] b, int off, int len) throws IOException {
    if (endOfStreamReached) {
      return -1;
    }

    skipDataIfNeeded();
    return increaseOffset(delegate.read(b, off, len));
  }

  @Override
  public synchronized int read() throws IOException {
    if (endOfStreamReached) {
      return -1;
    }

    skipDataIfNeeded();
    int r = delegate.read();
    if (r == -1) {
      endOfStreamReached = true;
    } else {
      increaseOffset(1);
    }

    return r;
  }

  @Override
  public synchronized long skip(long n) throws IOException {
    if (endOfStreamReached) {
      return 0;
    }

    skipDataIfNeeded();
    return increaseOffset(delegate.skip(n));
  }

  @Override
  public synchronized int available() throws IOException {
    if (endOfStreamReached) {
      return 0;
    }

    skipDataIfNeeded();
    return delegate.available();
  }

  @Override
  public synchronized void close() throws IOException {
    endOfStreamReached = true;
    delegate.close();
  }

  @Override
  public void mark(int readlimit) {
    // We don't support marking.
  }

  @Override
  public void reset() throws IOException {
    throw new IOException("Mark not supported");
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  /**
   * Switches the stream used.
   *
   * <p>The stream that is currently in use and the new stream will be used in further operations.
   * If this stream has already reached the end, {@code newStream} will be closed immediately and no
   * other action is taken. If the stream has not reached the end, any exception reported is due to
   * closing the stream currently in use, the new stream is not affected and this stream can still
   * be used to read from {@code newStream}.
   */
  synchronized void switchStream(InputStream newStream) throws IOException {
    if (newStream == delegate) {
      return;
    }

    try (InputStream oldDelegate = delegate) {
      delegate = newStream;
      needsSkipping = currentOffset;
    }
  }
}
