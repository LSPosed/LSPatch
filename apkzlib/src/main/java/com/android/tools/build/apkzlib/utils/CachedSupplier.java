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

import com.google.common.base.Supplier;

/**
 * Supplier that will cache a computed value and always supply the same value. It can be used to
 * lazily compute data. For example:
 *
 * <pre>{@code
 * CachedSupplier<Integer> value = new CachedSupplier<>(() -> {
 *     Integer result;
 *     // Do some expensive computation.
 *     return result;
 * });
 *
 * if (a) {
 *     // We need the result of the expensive computation.
 *     Integer r = value.get();
 * }
 *
 * if (b) {
 *     // We also need the result of the expensive computation.
 *     Integer r = value.get();
 * }
 *
 * // If neither a nor b are true, we avoid doing the computation at all.
 * }</pre>
 */
public class CachedSupplier<T> {

  /**
   * The cached data, {@code null} if computation resulted in {@code null}. It is also {@code null}
   * if the cached data has not yet been computed.
   */
  private T cached;

  /** Is the current data in {@link #cached} valid? */
  private boolean valid;

  /** Actual supplier of data, if computation is needed. */
  private final Supplier<T> supplier;

  /** Creates a new supplier. */
  public CachedSupplier(Supplier<T> supplier) {
    valid = false;
    this.supplier = supplier;
  }

  /**
   * Obtains the value.
   *
   * @return the value, either cached (if one exists) or computed
   */
  public synchronized T get() {
    if (!valid) {
      cached = supplier.get();
      valid = true;
    }

    return cached;
  }

  /**
   * Resets the cache forcing a {@code get()} on the supplier next time {@link #get()} is invoked.
   */
  public synchronized void reset() {
    cached = null;
    valid = false;
  }

  /**
   * In some cases, we may be able to precompute the cache value (or load it from somewhere we had
   * previously stored it). This method allows the cache value to be loaded.
   *
   * <p>If this method is invoked, then an invocation of {@link #get()} will not trigger an
   * invocation of the supplier provided in the constructor.
   *
   * @param t the new cache contents; will replace any currently cache content, if one exists
   */
  public synchronized void precomputed(T t) {
    cached = t;
    valid = true;
  }

  /**
   * Checks if the contents of the cache are valid.
   *
   * @return are there valid contents in the cache?
   */
  public synchronized boolean isValid() {
    return valid;
  }
}
