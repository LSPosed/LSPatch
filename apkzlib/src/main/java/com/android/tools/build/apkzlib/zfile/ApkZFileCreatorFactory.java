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

import com.android.tools.build.apkzlib.utils.IOExceptionWrapper;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import java.io.IOException;

/** Creates instances of {@link ApkZFileCreator}. */
public class ApkZFileCreatorFactory implements ApkCreatorFactory {

  /** Options for the {@link ZFileOptions} to use in all APKs. */
  private final ZFileOptions options;

  /**
   * Creates a new factory.
   *
   * @param options the options to use for all instances created
   */
  public ApkZFileCreatorFactory(ZFileOptions options) {
    this.options = options;
  }

  @Override
  public ApkCreator make(CreationData creationData) {
    try {
      return new ApkZFileCreator(creationData, options);
    } catch (IOException e) {
      throw new IOExceptionWrapper(e);
    }
  }
}
