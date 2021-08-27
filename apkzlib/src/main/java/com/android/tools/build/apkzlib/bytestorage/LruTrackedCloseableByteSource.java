package com.android.tools.build.apkzlib.bytestorage;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;

/**
 * Byte source that, until switched, will keep itself in the LRU queue. The byte source will
 * automatically remove itself from the queue once closed or moved to disk (see {@link
 * #moveToDisk(ByteStorage)}. This source should not be switched explicitly or tracking will not
 * work.
 *
 * <p>The source will consider an access to be opening a stream. Every time a stream is open the
 * source will move itself to the top of the LRU list.
 */
class LruTrackedCloseableByteSource extends SwitchableDelegateCloseableByteSource {
  /** The tracker being used. */
  private final LruTracker<LruTrackedCloseableByteSource> tracker;

  /** Are we still tracking usage? */
  private boolean tracking;

  /** Has the byte source been closed? */
  private boolean closed;

  /** Creates a new byte source based on the given source and using the provided tracker. */
  LruTrackedCloseableByteSource(
      CloseableByteSource delegate, LruTracker<LruTrackedCloseableByteSource> tracker)
      throws IOException {
    super(delegate);
    this.tracker = tracker;
    tracker.track(this);
    tracking = true;
    closed = false;
  }

  @Override
  public synchronized InputStream openStream() throws IOException {
    Preconditions.checkState(!closed);
    if (tracking) {
      tracker.access(this);
    }

    return super.openStream();
  }

  @Override
  protected synchronized void innerClose() throws IOException {
    closed = true;

    untrack();
    super.innerClose();
  }

  /**
   * Marks this source as not being tracked any more. May be called multiple times (only the first
   * one will do anything).
   */
  private synchronized void untrack() {
    if (tracking) {
      tracking = false;
      tracker.untrack(this);
    }
  }

  /**
   * Moves the contents of this source to a storage. This will untrack the source and switch its
   * contents to a new delegate provided by {@code diskStorage}.
   */
  synchronized void move(ByteStorage diskStorage) throws IOException {
    if (closed) {
      return;
    }

    CloseableByteSource diskSource = diskStorage.fromSource(this);
    untrack();
    switchSource(diskSource);
  }
}
