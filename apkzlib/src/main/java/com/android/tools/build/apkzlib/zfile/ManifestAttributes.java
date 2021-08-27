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

/** Java manifest attributes and some default values. */
public interface ManifestAttributes {
  /** Manifest attribute with the built by information. */
  String BUILT_BY = "Built-By";

  /** Manifest attribute with the created by information. */
  String CREATED_BY = "Created-By";

  /** Manifest attribute with the manifest version. */
  String MANIFEST_VERSION = "Manifest-Version";

  /** Manifest attribute value with the manifest version. */
  String CURRENT_MANIFEST_VERSION = "1.0";
}
