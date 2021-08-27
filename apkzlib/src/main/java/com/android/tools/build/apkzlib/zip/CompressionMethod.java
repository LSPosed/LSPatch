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

import javax.annotation.Nullable;

/** Enumeration with all known compression methods. */
public enum CompressionMethod {
  /** STORE method: data is stored without any compression. */
  STORE(0),

  /** DEFLATE method: data is stored compressed using the DEFLATE algorithm. */
  DEFLATE(8);

  /** Code, within the zip file, that identifies this compression method. */
  int methodCode;

  /**
   * Creates a new compression method.
   *
   * @param methodCode the code used in the zip file that identifies the compression method
   */
  CompressionMethod(int methodCode) {
    this.methodCode = methodCode;
  }

  /**
   * Obtains the compression method that corresponds to the provided code.
   *
   * @param code the code
   * @return the method or {@code null} if no method has the provided code
   */
  @Nullable
  static CompressionMethod fromCode(long code) {
    for (CompressionMethod method : values()) {
      if (method.methodCode == code) {
        return method;
      }
    }

    return null;
  }
}
