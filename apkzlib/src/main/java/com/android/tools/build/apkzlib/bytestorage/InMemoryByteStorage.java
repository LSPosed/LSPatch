/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.build.apkzlib.bytestorage;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.android.tools.build.apkzlib.zip.utils.CloseableDelegateByteSource;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Keeps track of used bytes allowing gauging memory usage. */
public class InMemoryByteStorage implements ByteStorage {

  /** Number of bytes currently in use. */
  private long bytesUsed;

  /** Maximum number of bytes used. */
  private long maxBytesUsed;

  @Override
  public CloseableByteSource fromStream(InputStream stream) throws IOException {
    byte[] data = ByteStreams.toByteArray(stream);
    updateUsage(data.length);
    return new CloseableDelegateByteSource(ByteSource.wrap(data), data.length) {
      @Override
      public synchronized void innerClose() throws IOException {
        super.innerClose();
        updateUsage(-sizeNoException());
      }
    };
  }

  @Override
  public CloseableByteSourceFromOutputStreamBuilder makeBuilder() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    return new AbstractCloseableByteSourceFromOutputStreamBuilder() {
      @Override
      protected void doWrite(byte[] b, int off, int len) throws IOException {
        output.write(b, off, len);
        updateUsage(len);
      }

      @Override
      protected CloseableByteSource doBuild() throws IOException {
        byte[] data = output.toByteArray();
        return new CloseableDelegateByteSource(ByteSource.wrap(data), data.length) {
          @Override
          protected synchronized void innerClose() throws IOException {
            super.innerClose();
            updateUsage(-data.length);
          }
        };
      }
    };
  }

  @Override
  public CloseableByteSource fromSource(ByteSource source) throws IOException {
    return fromStream(source.openStream());
  }

  /**
   * Updates the memory used by this tracker.
   *
   * @param delta the number of bytes to add or remove, if negative
   */
  private synchronized void updateUsage(long delta) {
    bytesUsed += delta;
    if (maxBytesUsed < bytesUsed) {
      maxBytesUsed = bytesUsed;
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

  @Override
  public void close() throws IOException {
    // Nothing to do on close.
  }
}
