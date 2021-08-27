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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Byte source that inflates another byte source. It assumed the inner byte source has deflated
 * data.
 */
public class InflaterByteSource extends CloseableByteSource {

  /** The stream factory for the deflated data. */
  private final CloseableByteSource deflatedSource;

  /**
   * Creates a new source.
   *
   * @param byteSource the factory for deflated data
   */
  public InflaterByteSource(CloseableByteSource byteSource) {
    deflatedSource = byteSource;
  }

  @Override
  public InputStream openStream() throws IOException {
    /*
     * The extra byte is a dummy byte required by the inflater. Weirdo.
     * (see the java.util.Inflater documentation). Looks like a hack...
     * "Oh, I need an extra dummy byte to allow for some... err... optimizations..."
     */
    ByteArrayInputStream hackByte = new ByteArrayInputStream(new byte[] {0});
    return new InflaterInputStream(
        new SequenceInputStream(deflatedSource.openStream(), hackByte), new Inflater(true));
  }

  @Override
  public void innerClose() throws IOException {
    deflatedSource.close();
  }
}
