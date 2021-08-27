/*
 * Copyright (C) 2015 The Android Open Source Project
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

import java.io.IOException;
import java.io.RandomAccessFile;

/** Utility class with utility methods for random access files. */
public final class RandomAccessFileUtils {

  private RandomAccessFileUtils() {}

  /**
   * Reads from an random access file until the provided array is filled. Data is read from the
   * current position in the file.
   *
   * @param raf the file to read data from
   * @param data the array that will receive the data
   * @throws IOException failed to read the data
   */
  public static void fullyRead(RandomAccessFile raf, byte[] data) throws IOException {
    int r;
    int p = 0;

    while ((r = raf.read(data, p, data.length - p)) > 0) {
      p += r;
      if (p == data.length) {
        break;
      }
    }

    if (p < data.length) {
      throw new IOException(
          "Failed to read "
              + data.length
              + " bytes from file. Only "
              + p
              + " bytes could be read.");
    }
  }
}
