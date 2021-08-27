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

package com.android.tools.build.apkzlib.utils;

/** Pair implementation to use with the {@code apkzlib} library. */
public class ApkZLibPair<T1, T2> {

  /** First value. */
  public T1 v1;

  /** Second value. */
  public T2 v2;

  /**
   * Creates a new pair.
   *
   * @param v1 the first value
   * @param v2 the second value
   */
  public ApkZLibPair(T1 v1, T2 v2) {
    this.v1 = v1;
    this.v2 = v2;
  }
}
