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

/**
 * A field rule defines an invariant (<em>i.e.</em>, a constraint) that has to be verified by a
 * field value.
 */
interface ZipFieldInvariant {

  /**
   * Evalutes the invariant against a value.
   *
   * @param value the value to check the invariant
   * @return is the invariant valid?
   */
  boolean isValid(long value);

  /**
   * Obtains the name of the invariant. Used for information purposes.
   *
   * @return the name of the invariant
   */
  String getName();
}
