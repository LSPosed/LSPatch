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

package com.android.tools.build.apkzlib.zip;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.io.Closer;
import java.io.Closeable;
import java.io.IOException;

/**
 * Container that has two bytes sources: one representing raw data and another processed data. In
 * case of compression, the raw data is the compressed data and the processed data is the
 * uncompressed data. It is valid for a RaP ("Raw-and-Processed") to contain the same byte sources
 * for both processed and raw data.
 */
public class ProcessedAndRawByteSources implements Closeable {

  /** The processed byte source. */
  private final CloseableByteSource processedSource;

  /** The processed raw source. */
  private final CloseableByteSource rawSource;

  /**
   * Creates a new container.
   *
   * @param processedSource the processed source
   * @param rawSource the raw source
   */
  public ProcessedAndRawByteSources(
      CloseableByteSource processedSource, CloseableByteSource rawSource) {
    this.processedSource = processedSource;
    this.rawSource = rawSource;
  }

  /**
   * Obtains a byte source that read the processed contents of the entry.
   *
   * @return a byte source
   */
  public CloseableByteSource getProcessedByteSource() {
    return processedSource;
  }

  /**
   * Obtains a byte source that reads the raw contents of an entry. This is the data that is
   * ultimately stored in the file and, in the case of compressed files, is the same data in the
   * source returned by {@link #getProcessedByteSource()}.
   *
   * @return a byte source
   */
  public CloseableByteSource getRawByteSource() {
    return rawSource;
  }

  @Override
  public void close() throws IOException {
    Closer closer = Closer.create();
    closer.register(processedSource);
    closer.register(rawSource);
    closer.close();
  }
}
