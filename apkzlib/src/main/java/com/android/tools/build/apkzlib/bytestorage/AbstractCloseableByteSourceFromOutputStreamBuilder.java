package com.android.tools.build.apkzlib.bytestorage;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.base.Preconditions;
import java.io.IOException;

/**
 * Abstract implementation of a {@link CloseableByteSourceFromOutputStreamBuilder} that simplifies
 * the implementation of concrete instances. It implements the state machine implied by the
 * interface contract and requires subclasses to implement two methods:
 * {@link #doWrite(byte[], int, int)} -- that actually does writing and {@link #doBuild()} that
 * builds the {@link CloseableByteSource].
 */
abstract class AbstractCloseableByteSourceFromOutputStreamBuilder
    extends CloseableByteSourceFromOutputStreamBuilder {

  /**
   * Array that allows {@link #write(int)} to delegate to {@link #write(byte[], int, int)} without
   * having to create an array for each invocation.
   */
  private final byte[] tempByte;

  /**
   * Has the builder been closed? If it has, then {@link #build()} may be called, but none of the
   * writing methods can.
   */
  private boolean closed;

  /**
   * Has the builder been built? If this is {@code true} then {@link #closed} is also {@code true}.
   */
  private boolean built;

  /** Creates a new builder. */
  AbstractCloseableByteSourceFromOutputStreamBuilder() {
    tempByte = new byte[1];
    closed = false;
    built = false;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    Preconditions.checkState(!closed);
    doWrite(b, off, len);
  }

  @Override
  public void write(int b) throws IOException {
    tempByte[0] = (byte) b;
    write(tempByte, 0, 1);
  }

  @Override
  public void close() throws IOException {
    closed = true;
  }

  @Override
  public CloseableByteSource build() throws IOException {
    Preconditions.checkState(!built);
    closed = true;
    built = true;

    return doBuild();
  }

  /**
   * Same as {@link #write(byte[], int, int)}, but with the guarantee that the source has not been
   * built and the builder is still open.
   *
   * @param b see {@link #write(byte[], int, int)}
   * @param off see {@link #write(byte[], int, int)}
   * @param len see {@link #write(byte[], int, int)}
   * @throws IOException see {@link #write(byte[], int, int)}
   */
  protected abstract void doWrite(byte[] b, int off, int len) throws IOException;

  /**
   * Builds the {@link CloseableByteSource} from the written data. This method is at most invoked
   * once.
   *
   * @return the new source that will contain all data written to the builder so far
   * @throws IOException failed to create the byte source
   */
  protected abstract CloseableByteSource doBuild() throws IOException;
}
