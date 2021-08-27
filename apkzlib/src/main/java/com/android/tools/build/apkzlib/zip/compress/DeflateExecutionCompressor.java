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

package com.android.tools.build.apkzlib.zip.compress;

import com.android.tools.build.apkzlib.bytestorage.ByteStorage;
import com.android.tools.build.apkzlib.bytestorage.CloseableByteSourceFromOutputStreamBuilder;
import com.android.tools.build.apkzlib.zip.CompressionMethod;
import com.android.tools.build.apkzlib.zip.CompressionResult;
import com.android.tools.build.apkzlib.zip.utils.ByteTracker;
import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.io.ByteStreams;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Compressor that uses deflate with an executor. */
public class DeflateExecutionCompressor extends ExecutorCompressor {

  /** Deflate compression level. */
  private final int level;

  /**
   * Creates a new compressor.
   *
   * @param executor the executor to run deflation tasks
   * @param level the compression level
   */
  public DeflateExecutionCompressor(Executor executor, int level) {
    super(executor);

    this.level = level;
  }

  @Deprecated
  public DeflateExecutionCompressor(Executor executor, ByteTracker tracker, int level) {
    this(executor, level);
  }

  @Override
  protected CompressionResult immediateCompress(CloseableByteSource source, ByteStorage storage)
      throws Exception {
    Deflater deflater = new Deflater(level, true);
    CloseableByteSourceFromOutputStreamBuilder resultBuilder = storage.makeBuilder();

    try (InputStream inputStream = source.openBufferedStream();
            DeflaterOutputStream dos = new DeflaterOutputStream(resultBuilder, deflater)) {
      ByteStreams.copy(inputStream, dos);
    }

    CloseableByteSource result = resultBuilder.build();
    if (result.size() >= source.size()) {
      result.close();
      return new CompressionResult(source, CompressionMethod.STORE, source.size());
    } else {
      return new CompressionResult(result, CompressionMethod.DEFLATE, result.size());
    }
  }
}
