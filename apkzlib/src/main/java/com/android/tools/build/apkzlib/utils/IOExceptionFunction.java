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

import com.google.common.base.Function;
import java.io.IOException;
import javax.annotation.Nullable;

/** Function that can throw an I/O Exception */
public interface IOExceptionFunction<F, T> {

  /**
   * Applies the function to the given input.
   *
   * @param input the input
   * @return the function result
   */
  @Nullable
  T apply(@Nullable F input) throws IOException;

  /**
   * Wraps a function that may throw an IO Exception throwing an {@link IOExceptionWrapper}.
   *
   * @param f the function
   */
  static <F, T> Function<F, T> asFunction(IOExceptionFunction<F, T> f) {
    return i -> {
      try {
        return f.apply(i);
      } catch (IOException e) {
        throw new IOExceptionWrapper(e);
      }
    };
  }
}
