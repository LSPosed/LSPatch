/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/**
 * Zip64 End of Central Directory Locator. Used to locate the Zip64 EOCD record in
 * the Zip64 format. This will be located right above the standard EOCD record, if it exists.
 */
class Zip64EocdLocator {
  /** Field in the record: the record signature, fixed at this value by the specification. */
  private static final ZipField.F4 F_SIGNATURE =
      new ZipField.F4(0, 0x07064b50, "Zip64 EOCD Locator signature");

  /**
   * Field in the record: the number of the disk where the Zip64 EOCD is located. This has to be
   * zero because multi-file archives are not supported.
   */
  private static final ZipField.F4 F_NUMBER_OF_DISK =
      new ZipField.F4(F_SIGNATURE.endOffset(), 0, "Number of disk with Zip64 EOCD");

  /**
   * Field in the record: the location of the zip64 EOCD record on the disk specified by
   * {@link #F_NUMBER_OF_DISK}.
   */
  private static final ZipField.F8 F_Z64_EOCD_OFFSET =
      new ZipField.F8(
          F_NUMBER_OF_DISK.endOffset(),
          "Offset of Zip64 EOCD",
          new ZipFieldInvariantNonNegative());

  /**
   * Field in the record: the total number of disks in the archive. This has to be zero because
   * multi-file archives are not supported.
   */
  private static final ZipField.F4 F_TOTAL_NUMBER_OF_DISKS =
      new ZipField.F4(
          F_Z64_EOCD_OFFSET.endOffset(), 0,"Total number of disks");


  public static final int LOCATOR_SIZE = F_TOTAL_NUMBER_OF_DISKS.endOffset();

  /**
   * Offset from the beginning of the archive to where the Zip64 End of Central Directory record
   * is located.
   */
  private final long z64EocdOffset;

  /** Supplier of the byte representation of the zip64 Eocd Locator. */
  private final CachedSupplier<byte[]> byteSupplier;

  /**
   * Creates a new Zip64 EOCD Locator, reading it from a byte source. This method will parse the
   * byte source and obtain the EOCD Locator. It will check that the byte source starts with the
   * EOCD Locator signature.
   *
   * @param bytes the byte buffer with the Locator data; when this method finishes, the byte buffer
   *     will have its position moved to the end of the Locator (the beginning of the standard EOCD)
   * @throws IOException failed to read information or the EOCD data is corrupt or invalid.
   */
  Zip64EocdLocator(ByteBuffer bytes) throws IOException {
    F_SIGNATURE.verify(bytes);
    F_NUMBER_OF_DISK.verify(bytes);
    long z64EocdOffset = F_Z64_EOCD_OFFSET.read(bytes);
    F_TOTAL_NUMBER_OF_DISKS.verify(bytes);

    Verify.verify(z64EocdOffset >= 0);
    this.z64EocdOffset = z64EocdOffset;
    byteSupplier = new CachedSupplier<>(this::computeByteRepresentation);
  }

  /**
   * Creates a new Zip64 EOCD Locator. This is used when generating an EOCD Locator for a
   * Zip64 EOCD that has been generated.
   *
   * @param z64EocdOffset offset position of the Zip64 EOCD from the beginning of the archive
   */
  Zip64EocdLocator(long z64EocdOffset) {
    Preconditions.checkArgument(z64EocdOffset >= 0, "z64EocdOffset < 0");

    this.z64EocdOffset = z64EocdOffset;
    byteSupplier = new CachedSupplier<>(this::computeByteRepresentation);
  }

  /**
   * Obtains the offset from the beginning of the archive to where the Zip64 EOCD is located.
   *
   * @return the Zip64 EOCD offset.
   */
  long getZ64EocdOffset() {
    return z64EocdOffset;
  }

  /**
   * Obtains the size of the Zip64 EOCD Locator
   *
   * @return the size, in bytes, of the EOCD Locator.<em> i.e. </em> 20.
   */
  long getSize() {
    return F_TOTAL_NUMBER_OF_DISKS.endOffset();
  }

  /**
   * Generates the EOCD Locator data.
   *
   * @return a byte representation of the EOCD Locator that has exactly {@link #getSize()} bytes
   * @throws IOException failed to generate the EOCD data.
   */
  byte[] toBytes() throws IOException {
    return byteSupplier.get();
  }

  /**
   * Computes the byte representation of the EOCD Locator.
   *
   * @return a byte representation of the Zip64 EOCD Locator that has exactly {@link #getSize()}
   *     bytes
   * @throws UncheckedIOException failed to generate the EOCD Locator data
   */
  private byte[] computeByteRepresentation() {
    ByteBuffer out = ByteBuffer.allocate(F_TOTAL_NUMBER_OF_DISKS.endOffset());

    try {
      F_SIGNATURE.write(out);
      F_NUMBER_OF_DISK.write(out);
      F_Z64_EOCD_OFFSET.write(out, z64EocdOffset);
      F_TOTAL_NUMBER_OF_DISKS.write(out);

      return out.array();
    } catch (IOException e) {
      throw new IOExceptionWrapper(e);
    }
  }
}
