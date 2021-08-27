package com.android.tools.build.apkzlib.bytestorage;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.io.Closer;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Byte source that delegates to another byte source that can be switched dynamically.
 *
 * <p>This byte source encloses another byte source (the delegate) and allows switching the
 * delegate. Switching is done transparently for the user (as long as the new byte source represents
 * the same data) maintaining all open streams working, but now streaming from the new source.
 */
class SwitchableDelegateCloseableByteSource extends CloseableByteSource {

  /** The current delegate. */
  private CloseableByteSource delegate;

  /** Has the byte source been closed? */
  private boolean closed;

  /**
   * Streams that have been opened, but not yet closed. These are all the streams that have to be
   * switched when we switch delegates.
   */
  private final List<SwitchableDelegateInputStream> nonClosedStreams;

  /** Creates a new source using {@code source} as delegate. */
  SwitchableDelegateCloseableByteSource(CloseableByteSource source) {
    this.delegate = source;
    nonClosedStreams = new ArrayList<>();
  }

  @Override
  protected synchronized void innerClose() throws IOException {
    closed = true;

    try (Closer closer = Closer.create()) {
      for (SwitchableDelegateInputStream stream : nonClosedStreams) {
        closer.register(stream);
      }

      nonClosedStreams.clear();
    }

    delegate.close();
  }

  @Override
  public synchronized InputStream openStream() throws IOException {
    SwitchableDelegateInputStream stream =
        new SwitchableDelegateInputStream(delegate.openStream()) {
          // Can't have a lock on the stream while we synchronize the removal of nonClosedStreams
          // because it can deadlock when called in parallel with switchSource as the lock order is
          // reversed. The lack of synchronization is OK because we don't access any data on the
          // stream anyway until super.close() is called.
          @SuppressWarnings("UnsynchronizedOverridesSynchronized")
          @Override
          public void close() throws IOException {
            // Remove the stream on close.
            synchronized (SwitchableDelegateCloseableByteSource.this) {
              nonClosedStreams.remove(this);
            }

            super.close();
          }
        };

    nonClosedStreams.add(stream);
    return stream;
  }

  /**
   * Switches the current source for {@code source}. All streams are kept valid. The current source
   * is closed.
   *
   * <p>If the current source has already been closed, {@code source} will also be closed and
   * nothing else is done.
   *
   * <p>Otherwise, as long as it is possible to open enough input streams from {@code source} to
   * replace all current input streams, the source if changed. Any errors while closing input
   * streams (which happens during switching -- see {@link
   * SwitchableDelegateInputStream#switchStream(InputStream)}) or closing the old source are
   * reported as thrown {@code IOException}
   */
  synchronized void switchSource(CloseableByteSource source) throws IOException {
    if (source == delegate) {
      return;
    }

    if (closed) {
      source.close();
      return;
    }

    List<InputStream> switchStreams = new ArrayList<>();
    for (int i = 0; i < nonClosedStreams.size(); i++) {
      switchStreams.add(source.openStream());
    }

    CloseableByteSource oldDelegate = delegate;
    delegate = source;

    // A bit of trickery. We want to call switchStream for all streams. switchStream will
    // successfully switch the stream even if it throws an exception (if it does, it means it
    // failed to close the old stream). So we want to continue switching and recording all
    // exceptions. Closer() has that logic already so we register each stream switch as a close
    // operation.
    try (Closer closer = Closer.create()) {
      for (int i = 0; i < nonClosedStreams.size(); i++) {
        SwitchableDelegateInputStream nonClosedStream = nonClosedStreams.get(i);
        InputStream switchStream = switchStreams.get(i);
        closer.register(() -> nonClosedStream.switchStream(switchStream));
      }

      closer.register(oldDelegate);
    }
  }
}
