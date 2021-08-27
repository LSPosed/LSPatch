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


/**
 * Information stored in the {@link CentralDirectoryHeader} that is related to compression and may
 * need to be computed lazily.
 */
public class CentralDirectoryHeaderCompressInfo {

  /** Version of zip file that only supports stored files. */
  public static final long VERSION_WITH_STORE_FILES_ONLY = 10L;

  /** Version of zip file that only supports directories and deflated files. */
  public static final long VERSION_WITH_DIRECTORIES_AND_DEFLATE = 20L;

  /** Version of zip file that only supports ZIP64 format extensions */
  public static final long VERSION_WITH_ZIP64_EXTENSIONS = 45L;

  /** Version of zip file that uses central file encryption and version 2 of the Zip64 EOCD */
  public static final long VERSION_WITH_CENTRAL_FILE_ENCRYPTION = 62L;

  /** The compression method. */
  private final CompressionMethod method;

  /** Size of the file compressed. 0 if the file has no data. */
  private final long compressedSize;

  /** Version needed to extract the zip. */
  private final long versionExtract;

  /**
   * Creates new compression information for the central directory header.
   *
   * @param method the compression method
   * @param compressedSize the compressed size
   * @param versionToExtract minimum version to extract (typically {@link
   *     #VERSION_WITH_STORE_FILES_ONLY} or {@link #VERSION_WITH_DIRECTORIES_AND_DEFLATE})
   */
  public CentralDirectoryHeaderCompressInfo(
      CompressionMethod method, long compressedSize, long versionToExtract) {
    this.method = method;
    this.compressedSize = compressedSize;
    versionExtract = versionToExtract;
  }

  /**
   * Creates new compression information for the central directory header.
   *
   * @param header the header this information relates to
   * @param method the compression method
   * @param compressedSize the compressed size
   */
  public CentralDirectoryHeaderCompressInfo(
      CentralDirectoryHeader header, CompressionMethod method, long compressedSize) {
    this.method = method;
    this.compressedSize = compressedSize;

   if (header.getName().endsWith("/") || method == CompressionMethod.DEFLATE) {
      /*
       * Directories and compressed files only in version 2.0.
       */
      versionExtract = VERSION_WITH_DIRECTORIES_AND_DEFLATE;
    } else {
      versionExtract = VERSION_WITH_STORE_FILES_ONLY;
    }
  }

  /**
   * Obtains the compression data size.
   *
   * @return the compressed data size
   */
  public long getCompressedSize() {
    return compressedSize;
  }

  /**
   * Obtains the compression method.
   *
   * @return the compression method
   */
  public CompressionMethod getMethod() {
    return method;
  }

  /**
   * Obtains the minimum version for extract.
   *
   * @return the minimum version
   */
  long getVersionExtract() {
    return versionExtract;
  }
}
