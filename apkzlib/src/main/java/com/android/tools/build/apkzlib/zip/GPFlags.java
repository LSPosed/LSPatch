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

package com.android.tools.build.apkzlib.zip;

import java.io.IOException;

/**
 * General purpose bit flags. Contains the encoding of the zip's general purpose bits.
 *
 * <p>We don't really care about the method bit(s). These are bits 1 and 2. Here are the values:
 *
 * <ul>
 *   <li>0 (00): Normal (-en) compression option was used.
 *   <li>1 (01): Maximum (-exx/-ex) compression option was used.
 *   <li>2 (10): Fast (-ef) compression option was used.
 *   <li>3 (11): Super Fast (-es) compression option was used.
 * </ul>
 */
class GPFlags {

  /** Is the entry encrypted? */
  private static final int BIT_ENCRYPTION = 1;

  /** Has CRC computation been deferred and, therefore, does a data description block exist? */
  private static final int BIT_DEFERRED_CRC = (1 << 3);

  /** Is enhanced deflating used? */
  private static final int BIT_ENHANCED_DEFLATING = (1 << 4);

  /** Does the entry contain patched data? */
  private static final int BIT_PATCHED_DATA = (1 << 5);

  /** Is strong encryption used? */
  private static final int BIT_STRONG_ENCRYPTION = (1 << 6) | (1 << 13);

  /**
   * If this bit is set the filename and comment fields for this file must be encoded using UTF-8.
   */
  private static final int BIT_EFS = (1 << 11);

  /** Unused bits. */
  private static final int BIT_UNUSED =
      (1 << 7) | (1 << 8) | (1 << 9) | (1 << 10) | (1 << 14) | (1 << 15);

  /** Bit flag value. */
  private final long value;

  /** Has the CRC computation beeen deferred? */
  private boolean deferredCrc;

  /** Is the file name encoded in UTF-8? */
  private boolean utf8FileName;

  /**
   * Creates a new flags object.
   *
   * @param value the value of the bit mask
   */
  private GPFlags(long value) {
    this.value = value;

    deferredCrc = ((value & BIT_DEFERRED_CRC) != 0);
    utf8FileName = ((value & BIT_EFS) != 0);
  }

  /**
   * Obtains the flags value.
   *
   * @return the value of the bit mask
   */
  public long getValue() {
    return value;
  }

  /**
   * Is the CRC computation deferred?
   *
   * @return is the CRC computation deferred?
   */
  public boolean isDeferredCrc() {
    return deferredCrc;
  }

  /**
   * Is the file name encoded in UTF-8?
   *
   * @return is the file name encoded in UTF-8?
   */
  public boolean isUtf8FileName() {
    return utf8FileName;
  }

  /**
   * Creates a new bit mask.
   *
   * @param utf8Encoding should UTF-8 encoding be used?
   * @return the new bit mask
   */
  static GPFlags make(boolean utf8Encoding) {
    long flags = 0;

    if (utf8Encoding) {
      flags |= BIT_EFS;
    }

    return new GPFlags(flags);
  }

  /**
   * Creates the flag information from a byte. This method will also validate that only supported
   * options are defined in the flag.
   *
   * @param bits the bit mask
   * @return the created flag information
   * @throws IOException unsupported options are used in the bit mask
   */
  static GPFlags from(long bits) throws IOException {
    if ((bits & BIT_ENCRYPTION) != 0) {
      throw new IOException("Zip files with encrypted of entries not supported.");
    }

    if ((bits & BIT_ENHANCED_DEFLATING) != 0) {
      throw new IOException("Enhanced deflating not supported.");
    }

    if ((bits & BIT_PATCHED_DATA) != 0) {
      throw new IOException("Compressed patched data not supported.");
    }

    if ((bits & BIT_STRONG_ENCRYPTION) != 0) {
      throw new IOException("Strong encryption not supported.");
    }

    if ((bits & BIT_UNUSED) != 0) {
      throw new IOException(
          "Unused bits set in directory entry. Weird. I don't know what's " + "going on.");
    }

    if ((bits & 0xffffffff00000000L) != 0) {
      throw new IOException("Unsupported bits after 32.");
    }

    return new GPFlags(bits);
  }
}
