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

package com.android.tools.build.apkzlib.zfile;

/** Describes how native libs should be packaged. */
public enum NativeLibrariesPackagingMode {
  /** Native libs are packaged as any other file. */
  COMPRESSED,

  /**
   * Native libs are packaged uncompressed and page-aligned, so they can be mapped into memory at
   * runtime.
   *
   * <p>Support for this mode was added in Android 23, it only works if the {@code
   * extractNativeLibs} attribute is set in the manifest.
   */
  UNCOMPRESSED_AND_ALIGNED;
}
