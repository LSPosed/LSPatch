/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.build.apkzlib.utils.CachedSupplier;
import com.android.tools.build.apkzlib.utils.IOExceptionWrapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/** End Of Central Directory record in a zip file. */
class Eocd {

  /** Max total records that can be specified by the standard EOCD. */
  static final long MAX_TOTAL_RECORDS = 0xFFFFL;

  /** Max size of the Central Directory that can be specified by the standard EOCD. */
  static final long MAX_CD_SIZE = 0xFFFFFFFFL;

  /** Max offset of the Central Directory that can be specified by the standard EOCD. */
  static final long MAX_CD_OFFSET = 0xFFFFFFFFL;

  /** Field in the record: the record signature, fixed at this value by the specification. */
  private static final ZipField.F4 F_SIGNATURE = new ZipField.F4(0, 0x06054b50, "EOCD signature");

  /**
   * Field in the record: the number of the disk where the EOCD is located. It has to be zero
   * because we do not support multi-file archives.
   */
  private static final ZipField.F2 F_NUMBER_OF_DISK =
      new ZipField.F2(F_SIGNATURE.endOffset(), 0, "Number of this disk");

  /**
   * Field in the record: the number of the disk where the Central Directory starts. Has to be zero
   * because we do not support multi-file archives.
   */
  private static final ZipField.F2 F_DISK_CD_START =
      new ZipField.F2(F_NUMBER_OF_DISK.endOffset(), 0, "Disk where CD starts");

  /**
   * Field in the record: the number of entries in the Central Directory on this disk. Because we do
   * not support multi-file archives, this is the same as {@link #F_RECORDS_TOTAL}.
   */
  private static final ZipField.F2 F_RECORDS_DISK =
      new ZipField.F2(
          F_DISK_CD_START.endOffset(), "Record on disk count", new ZipFieldInvariantNonNegative());

  /**
   * Field in the record: the total number of entries in the Central Directory. This value will be
   * {@link #MAX_TOTAL_RECORDS} if the file is in the Zip64 format, and the Central Directory holds
   * at least {@link #MAX_TOTAL_RECORDS} entries.
   */
  private static final ZipField.F2 F_RECORDS_TOTAL =
      new ZipField.F2(
          F_RECORDS_DISK.endOffset(),
          "Total records",
          new ZipFieldInvariantNonNegative(),
          new ZipFieldInvariantMaxValue(Integer.MAX_VALUE));

  /**
   * Field in the record: number of bytes of the Central Directory. This is not private because it
   * is required in unit tests. This value will be {@link #MAX_CD_SIZE} if the file is in the Zip64
   * format, and the Central Directory is at least {@link #MAX_CD_SIZE} bytes.
   */
  @VisibleForTesting
  static final ZipField.F4 F_CD_SIZE =
      new ZipField.F4(
          F_RECORDS_TOTAL.endOffset(), "Directory size", new ZipFieldInvariantNonNegative());

  /**
   * Field in the record: offset, from the archive start, where the Central Directory starts. This
   * is not private because it is required in unit tests. This value will be {@link #MAX_CD_OFFSET}
   * if the file is in the Zip64 format, and the Central Directory is at least
   * {@link #MAX_CD_OFFSET} bytes.
   */
  @VisibleForTesting
  static final ZipField.F4 F_CD_OFFSET =
      new ZipField.F4(
          F_CD_SIZE.endOffset(), "Directory offset", new ZipFieldInvariantNonNegative());

  /**
   * Field in the record: number of bytes of the file comment (located at the end of the EOCD
   * record).
   */
  private static final ZipField.F2 F_COMMENT_SIZE =
      new ZipField.F2(
          F_CD_OFFSET.endOffset(), "File comment size", new ZipFieldInvariantNonNegative());

  /** Number of entries in the central directory. */
  private final long totalRecords;

  /** Offset from the beginning of the archive where the Central Directory is located. */
  private final long directoryOffset;

  /** Number of bytes of the Central Directory. */
  private final long directorySize;

  /** Contents of the EOCD comment. */
  private final byte[] comment;

  /** Supplier of the byte representation of the EOCD. */
  private final CachedSupplier<byte[]> byteSupplier;

  /**
   * Creates a new EOCD, reading it from a byte source. This method will parse the byte source and
   * obtain the EOCD. It will check that the byte source starts with the EOCD signature.
   *
   * @param bytes the byte buffer with the EOCD data; when this method finishes, the byte buffer's
   *     position will have moved to the end of the EOCD
   * @throws IOException failed to read information or the EOCD data is corrupt or invalid
   */
  Eocd(ByteBuffer bytes) throws IOException {

    /*
     * Read the EOCD record.
     */
    F_SIGNATURE.verify(bytes);
    F_NUMBER_OF_DISK.verify(bytes);
    F_DISK_CD_START.verify(bytes);
    long totalRecords1 = F_RECORDS_DISK.read(bytes);
    long totalRecords2 = F_RECORDS_TOTAL.read(bytes);
    long directorySize = F_CD_SIZE.read(bytes);
    long directoryOffset = F_CD_OFFSET.read(bytes);
    int commentSize = Ints.checkedCast(F_COMMENT_SIZE.read(bytes));

    /*
     * Some sanity checks.
     */
    if (totalRecords1 != totalRecords2) {
      throw new IOException(
          "Zip states records split in multiple disks, which is not " + "supported.");
    }

    Verify.verify(totalRecords1 <= Integer.MAX_VALUE);

    totalRecords = Ints.checkedCast(totalRecords1);
    this.directorySize = directorySize;
    this.directoryOffset = directoryOffset;

    if (bytes.remaining() < commentSize) {
      throw new IOException(
          "Corrupt EOCD record: not enough data for comment (comment "
              + "size is "
              + commentSize
              + ").");
    }

    comment = new byte[commentSize];
    bytes.get(comment);
    byteSupplier = new CachedSupplier<>(this::computeByteRepresentation);
  }

  /**
   * Creates a new EOCD. This is used when generating an EOCD for an Central Directory that has just
   * been generated. The EOCD will be generated without any comment.
   *
   * @param totalRecords total number of records in the directory
   * @param directoryOffset offset, since beginning of archive, where the Central Directory is
   *     located
   * @param directorySize number of bytes of the Central Directory
   * @param comment the EOCD comment
   */
  Eocd(long totalRecords, long directoryOffset, long directorySize, byte[] comment) {
    Preconditions.checkArgument(totalRecords >= 0, "totalRecords < 0");
    Preconditions.checkArgument(directoryOffset >= 0, "directoryOffset < 0");
    Preconditions.checkArgument(directorySize >= 0, "directorySize < 0");

    this.totalRecords = totalRecords;
    this.directoryOffset = directoryOffset;
    this.directorySize = directorySize;
    this.comment = comment;
    byteSupplier = new CachedSupplier<>(this::computeByteRepresentation);
  }

  /**
   * Obtains the number of records in the Central Directory.
   *
   * @return the number of records
   */
  long getTotalRecords() {
    return totalRecords;
  }

  /**
   * Obtains the offset since the beginning of the zip archive where the Central Directory is
   * located.
   *
   * @return the offset where the Central Directory is located
   */
  long getDirectoryOffset() {
    return directoryOffset;
  }

  /**
   * Obtains the size of the Central Directory.
   *
   * @return the number of bytes that make up the Central Directory
   */
  long getDirectorySize() {
    return directorySize;
  }

  /**
   * Obtains the size of the EOCD.
   *
   * @return the size, in bytes, of the EOCD
   */
  long getEocdSize() {
    return (long) F_COMMENT_SIZE.endOffset() + comment.length;
  }

  /**
   * Generates the EOCD data.
   *
   * @return a byte representation of the EOCD that has exactly {@link #getEocdSize()} bytes
   * @throws IOException failed to generate the EOCD data
   */
  byte[] toBytes() throws IOException {
    return byteSupplier.get();
  }

  /**
   * Obtains the comment in the EOCD.
   *
   * @return the comment exactly as it is represented in the file (no encoding conversion is
   * done)
   */
  byte[] getComment() {
    byte[] commentCopy = new byte[comment.length];
    System.arraycopy(comment, 0, commentCopy, 0, comment.length);
    return commentCopy;
  }

  /**
   * Computes the byte representation of the EOCD.
   *
   * @return a byte representation of the EOCD that has exactly {@link #getEocdSize()} bytes
   * @throws UncheckedIOException failed to generate the EOCD data
   */
  private byte[] computeByteRepresentation() {
    ByteBuffer out = ByteBuffer.allocate(F_COMMENT_SIZE.endOffset() + comment.length);

    try {
      F_SIGNATURE.write(out);
      F_NUMBER_OF_DISK.write(out);
      F_DISK_CD_START.write(out);
      F_RECORDS_DISK.write(out, totalRecords);
      F_RECORDS_TOTAL.write(out, totalRecords);
      F_CD_SIZE.write(out, directorySize);
      F_CD_OFFSET.write(out, directoryOffset);
      F_COMMENT_SIZE.write(out, comment.length);
      out.put(comment);

      return out.array();
    } catch (IOException e) {
      throw new IOExceptionWrapper(e);
    }
  }
}
