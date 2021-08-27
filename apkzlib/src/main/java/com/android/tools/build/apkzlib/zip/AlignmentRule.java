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


/** An alignment rule defines how to a file should be aligned in a zip, based on its name. */
public interface AlignmentRule {

  /** Alignment value of files that do not require alignment. */
  int NO_ALIGNMENT = 1;

  /**
   * Obtains the alignment this rule computes for a given path.
   *
   * @param path the path in the zip file
   * @return the alignment value, always greater than {@code 0}; if this rule places no restrictions
   *     on the provided path, then {@link AlignmentRule#NO_ALIGNMENT} is returned
   */
  int alignment(String path);
}
