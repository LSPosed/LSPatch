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

package com.android.tools.build.apkzlib.zip.utils;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import javax.annotation.Nullable;

/** Closeable byte source that delegates to another byte source. */
public class CloseableDelegateByteSource extends CloseableByteSource {

  /** The byte source we delegate all operations to. {@code null} if disposed. */
  @Nullable private ByteSource inner;

  /**
   * Size of the byte source. This is the same as {@code inner.size()} (when {@code inner} is not
   * {@code null}), but we keep it separate to avoid calling {@code inner.size()} because it might
   * throw {@code IOException}.
   */
  private final long mSize;

  /**
   * Creates a new byte source.
   *
   * @param inner the inner byte source
   * @param size the size of the source
   */
  public CloseableDelegateByteSource(ByteSource inner, long size) {
    this.inner = inner;
    mSize = size;
  }

  /**
   * Obtains the inner byte source. Will throw an exception if the inner by byte source has been
   * disposed of.
   *
   * @return the inner byte source
   */
  private synchronized ByteSource get() {
    if (inner == null) {
      throw new ByteSourceDisposedException();
    }

    return inner;
  }

  /** Mark the byte source as disposed. */
  @Override
  protected synchronized void innerClose() throws IOException {
    if (inner == null) {
      return;
    }

    inner = null;
  }

  /**
   * Obtains the size of this byte source. Equivalent to {@link #size()} but not throwing {@code
   * IOException}.
   *
   * @return the size of the byte source
   */
  public long sizeNoException() {
    return mSize;
  }

  @Override
  public CharSource asCharSource(Charset charset) {
    return get().asCharSource(charset);
  }

  @Override
  public InputStream openBufferedStream() throws IOException {
    return get().openBufferedStream();
  }

  @Override
  public ByteSource slice(long offset, long length) {
    return get().slice(offset, length);
  }

  @Override
  public boolean isEmpty() throws IOException {
    return get().isEmpty();
  }

  @Override
  public long size() throws IOException {
    return get().size();
  }

  @Override
  public long copyTo(OutputStream output) throws IOException {
    return get().copyTo(output);
  }

  @Override
  public long copyTo(ByteSink sink) throws IOException {
    return get().copyTo(sink);
  }

  @Override
  public byte[] read() throws IOException {
    return get().read();
  }

  @Override
  public <T> T read(ByteProcessor<T> processor) throws IOException {
    return get().read(processor);
  }

  @Override
  public HashCode hash(HashFunction hashFunction) throws IOException {
    return get().hash(hashFunction);
  }

  @Override
  public boolean contentEquals(ByteSource other) throws IOException {
    return get().contentEquals(other);
  }

  @Override
  public InputStream openStream() throws IOException {
    return get().openStream();
  }

  /** Exception thrown when trying to use a byte source that has been disposed. */
  private static class ByteSourceDisposedException extends RuntimeException {

    /** Creates a new exception. */
    private ByteSourceDisposedException() {
      super(
          "Byte source was created by a ByteTracker and is now disposed. If you see "
              + "this message, then there is a bug.");
    }
  }
}
