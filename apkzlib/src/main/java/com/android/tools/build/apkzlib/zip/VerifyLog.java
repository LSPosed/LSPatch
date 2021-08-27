/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;

/**
 * The verify log contains verification messages. It is used to capture validation issues with a zip
 * file or with parts of a zip file.
 */
public interface VerifyLog {

  /**
   * Logs a message.
   *
   * @param message the message to verify
   */
  void log(String message);

  /**
   * Obtains all save logged messages.
   *
   * @return the logged messages
   */
  ImmutableList<String> getLogs();

  /**
   * Performs verification of a non-critical condition, logging a message if the condition is not
   * verified.
   *
   * @param condition the condition
   * @param message the message to write if {@code condition} is {@code false}.
   * @param args arguments for formatting {@code message} using {@code String.format}
   */
  default void verify(boolean condition, String message, Object... args) {
    if (!condition) {
      log(String.format(message, args));
    }
  }
}
