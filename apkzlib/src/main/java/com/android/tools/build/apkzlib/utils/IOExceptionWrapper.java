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

import java.io.IOException;

/**
 * Runtime exception used to encapsulate an IO Exception. This is used to allow throwing I/O
 * exceptions in functional interfaces that do not allow it and catching the exception afterwards.
 */
public class IOExceptionWrapper extends RuntimeException {

  /**
   * Creates a new exception.
   *
   * @param e the I/O exception to encapsulate
   */
  public IOExceptionWrapper(IOException e) {
    super(e);
  }

  @Override
  public IOException getCause() {
    return (IOException) super.getCause();
  }
}
