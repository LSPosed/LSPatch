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

import java.io.IOException;
import java.io.InputStream;

/**
 * Input stream that reads only a limited number of bytes from another input stream before reporting
 * EOF. When closed, this stream will not close the underlying stream.
 *
 * <p>If the underlying stream does not have enough data, this stream will read all available data
 * from the underlying stream.
 */
class LimitedInputStream extends InputStream {
  /** Where the data comes from. */
  private final InputStream input;

  /** How many bytes remain in this stream. */
  private long remaining;

  /** Has EOF been detected in {@link #input}? */
  private boolean eofDetected;

  /**
   * Creates a new input stream.
   *
   * @param input where to read data from
   * @param maximum the maximum number of bytes to read from {@code input}
   */
  LimitedInputStream(InputStream input, long maximum) {
    this.input = input;
    this.remaining = maximum;
    this.eofDetected = false;
  }

  @Override
  public int read() throws IOException {
    if (remaining == 0) {
      return -1;
    }

    int r = input.read();
    if (r >= 0) {
      remaining--;
    } else {
      eofDetected = true;
    }

    return r;
  }

  @Override
  public int read(byte[] whereTo, int offset, int length) throws IOException {
    if (remaining == 0) {
      return -1;
    }

    int toRead = (int) Math.min(remaining, length);
    int r = input.read(whereTo, offset, toRead);
    if (r >= 0) {
      remaining -= r;
    } else {
      eofDetected = true;
    }

    return r;
  }

  /** Returns {@code true} if EOF has been detected in the {@code input} stream. */
  boolean isInputFinished() {
    return eofDetected;
  }
}
