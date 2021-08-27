/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.build.apkzlib.zip.utils;

import com.google.common.io.ByteSource;
import java.io.Closeable;
import java.io.IOException;

/**
 * Byte source that can be closed. Closing a byte source allows releasing any resources associated
 * with it. This should not be confused with closing streams. For example, {@link ByteTracker} uses
 * {@code CloseableByteSources} to know when the data associated with the byte source can be
 * released.
 */
public abstract class CloseableByteSource extends ByteSource implements Closeable {

  /** Has the source been closed? */
  private boolean closed;

  /** Creates a new byte source. */
  public CloseableByteSource() {
    closed = false;
  }

  @Override
  public final synchronized void close() throws IOException {
    if (closed) {
      return;
    }

    try {
      innerClose();
    } finally {
      closed = true;
    }
  }

  /**
   * Closes the by source. This method is only invoked once, even if {@link #close()} is called
   * multiple times.
   *
   * @throws IOException failed to close
   */
  protected abstract void innerClose() throws IOException;
}
