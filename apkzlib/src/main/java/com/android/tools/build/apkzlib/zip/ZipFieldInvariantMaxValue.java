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

/** Invariant checking a zip field does not exceed a threshold. */
class ZipFieldInvariantMaxValue implements ZipFieldInvariant {

  /** The maximum value allowed. */
  private final long max;

  /**
   * Creates a new invariant.
   *
   * @param max the maximum value allowed for the field
   */
  ZipFieldInvariantMaxValue(long max) {
    this.max = max;
  }

  @Override
  public boolean isValid(long value) {
    return value <= max;
  }

  @Override
  public String getName() {
    return "Maximum value " + max;
  }
}
