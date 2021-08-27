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
import com.google.common.io.ByteSource;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for a storage that will temporarily save bytes. There are several factory methods to
 * create byte sources from several inputs, all of which may be discarded after the byte source has
 * been created. The data is saved in the storage and will be kept until the byte source is closed.
 */
public interface ByteStorage extends Closeable {
  /**
   * Creates a new byte source by fully reading an input stream.
   *
   * @param stream the input stream
   * @return a byte source containing the cached data from the given stream
   * @throws IOException failed to read the stream
   */
  CloseableByteSource fromStream(InputStream stream) throws IOException;

  /**
   * Creates a builder that is an output stream and can create a byte source.
   *
   * @return a builder where data can be written to and a {@link CloseableByteSource} can eventually
   *     be obtained from
   * @throws IOException failed to create the builder; this may happen if the builder require some
   *     preparation such as temporary storage allocation that may fail
   */
  CloseableByteSourceFromOutputStreamBuilder makeBuilder() throws IOException;

  /**
   * Creates a new byte source from another byte source.
   *
   * @param source the byte source to copy data from
   * @return the tracked byte source
   * @throws IOException failed to read data from the byte source
   */
  CloseableByteSource fromSource(ByteSource source) throws IOException;

  /**
   * Obtains the number of bytes currently used.
   *
   * @return the number of bytes
   */
  long getBytesUsed();

  /**
   * Obtains the maximum number of bytes ever used by this tracker.
   *
   * @return the number of bytes
   */
  long getMaxBytesUsed();
}
