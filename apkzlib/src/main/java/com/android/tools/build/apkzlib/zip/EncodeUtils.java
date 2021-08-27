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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

/** Utilities to encode and decode file names in zips. */
public class EncodeUtils {

  /** Utility class: no constructor. */
  private EncodeUtils() {
    /*
     * Nothing to do.
     */
  }

  /**
   * Decodes a file name.
   *
   * @param bytes the raw data buffer to read from
   * @param length the number of bytes in the raw data buffer containing the string to decode
   * @param flags the zip entry flags
   * @return the decode file name
   */
  public static String decode(ByteBuffer bytes, int length, GPFlags flags) throws IOException {
    if (bytes.remaining() < length) {
      throw new IOException(
          "Only "
              + bytes.remaining()
              + " bytes exist in the buffer, but "
              + "length is "
              + length
              + ".");
    }

    byte[] stringBytes = new byte[length];
    bytes.get(stringBytes);
    return decode(stringBytes, flags);
  }

  /**
   * Decodes a file name.
   *
   * @param data the raw data
   * @param flags the zip entry flags
   * @return the decode file name
   */
  public static String decode(byte[] data, GPFlags flags) {
    return decode(data, flagsCharset(flags));
  }

  /**
   * Decodes a file name.
   *
   * @param data the raw data
   * @param charset the charset to use
   * @return the decode file name
   */
  private static String decode(byte[] data, Charset charset) {
    try {
      return charset
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(data))
          .toString();
    } catch (CharacterCodingException e) {
      // If we're trying to decode ASCII, try UTF-8. Otherwise, revert to the default
      // behavior (usually replacing invalid characters).
      if (charset.equals(US_ASCII)) {
        return decode(data, UTF_8);
      } else {
        return charset.decode(ByteBuffer.wrap(data)).toString();
      }
    }
  }

  /**
   * Encodes a file name.
   *
   * @param name the name to encode
   * @param flags the zip entry flags
   * @return the encoded file name
   */
  public static byte[] encode(String name, GPFlags flags) {
    Charset charset = flagsCharset(flags);
    ByteBuffer bytes = charset.encode(name);
    byte[] result = new byte[bytes.remaining()];
    bytes.get(result);
    return result;
  }

  /**
   * Obtains the charset to encode and decode zip entries, given a set of flags.
   *
   * @param flags the flags
   * @return the charset to use
   */
  private static Charset flagsCharset(GPFlags flags) {
    if (flags.isUtf8FileName()) {
      return UTF_8;
    } else {
      return US_ASCII;
    }
  }

  /**
   * Checks if some text may be encoded using ASCII.
   *
   * @param text the text to check
   * @return can it be encoded using ASCII?
   */
  public static boolean canAsciiEncode(String text) {
    return US_ASCII.newEncoder().canEncode(text);
  }
}
