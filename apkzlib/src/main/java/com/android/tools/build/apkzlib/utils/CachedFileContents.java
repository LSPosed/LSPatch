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

import com.google.common.base.Objects;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * A cache for file contents. The cache allows closing a file and saving in memory its contents (or
 * some related information). It can then be used to check if the contents are still valid at some
 * later time. Typical usage flow is:
 *
 * <p>
 *
 * <pre>{@code
 * Object fileRepresentation = // ...
 * File toWrite = // ...
 * // Write file contents and update in memory representation
 * CachedFileContents<Object> contents = new CachedFileContents<Object>(toWrite);
 * contents.closed(fileRepresentation);
 *
 * // Later, when data is needed:
 * if (contents.isValid()) {
 *     fileRepresentation = contents.getCache();
 * } else {
 *     // Re-read the file and recreate the file representation
 * }
 * }</pre>
 *
 * @param <T> the type of cached contents
 */
public class CachedFileContents<T> {

  /** The file. */
  private final File file;

  /** Time when last closed (time when {@link #closed(Object)} was invoked). */
  private long lastClosed;

  /** Size of the file when last closed. */
  private long size;

  /** Hash of the file when closed. {@code null} if hashing failed for some reason. */
  @Nullable private HashCode hash;

  /** Cached data associated with the file. */
  @Nullable private T cache;

  /**
   * Creates a new contents. When the file is written, {@link #closed(Object)} should be invoked to
   * set the cache.
   *
   * @param file the file
   */
  public CachedFileContents(File file) {
    this.file = file;
  }

  /**
   * Should be called when the file's contents are set and the file closed. This will save the cache
   * and register the file's timestamp to later detect if it has been modified.
   *
   * <p>This method can be called as many times as the file has been written.
   *
   * @param cache an optional cache to save
   */
  public void closed(@Nullable T cache) {
    this.cache = cache;
    lastClosed = file.lastModified();
    size = file.length();
    hash = hashFile();
  }

  /**
   * Are the cached contents still valid? If this method determines that the file has been modified
   * since the last time {@link #closed(Object)} was invoked.
   *
   * @return are the cached contents still valid? If this method returns {@code false}, the cache is
   *     cleared
   */
  public boolean isValid() {
    boolean valid = true;

    if (!file.exists()) {
      valid = false;
    }

    if (valid && file.lastModified() != lastClosed) {
      valid = false;
    }

    if (valid && file.length() != size) {
      valid = false;
    }

    if (valid && !Objects.equal(hash, hashFile())) {
      valid = false;
    }

    if (!valid) {
      cache = null;
    }

    return valid;
  }

  /**
   * Obtains the cached data set with {@link #closed(Object)} if the file has not been modified
   * since {@link #closed(Object)} was invoked.
   *
   * @return the last cached data or {@code null} if the file has been modified since {@link
   *     #closed(Object)} has been invoked
   */
  @Nullable
  public T getCache() {
    return cache;
  }

  /**
   * Computes the hashcode of the cached file.
   *
   * @return the hash code
   */
  @Nullable
  private HashCode hashFile() {
    try {
      return Files.asByteSource(file).hash(Hashing.crc32());
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Obtains the file used for caching.
   *
   * @return the file; this file always exists and contains the old (cached) contents of the file
   */
  public File getFile() {
    return file;
  }
}
