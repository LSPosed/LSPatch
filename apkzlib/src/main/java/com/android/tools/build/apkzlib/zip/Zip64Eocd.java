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
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Zip64 End of Central Directory record in a zip file.
 */
public class Zip64Eocd {

  /**
   * Default "version made by" field: upper byte needs to be 0 to set to MS-DOS compatibility. Lower
   * byte can be anything, really. We use 0x18 because aapt uses 0x17 :)
   */
  private static final int DEFAULT_VERSION_MADE_BY = 0x0018;

  /**
   * Minimum size that can be stored in the {@link #F_EOCD_SIZE} field of the record.
   */
  private static final int MIN_EOCD_SIZE = 44;

  /** Field in the record: the record signature, fixed at this value by the specification */
  private static final ZipField.F4 F_SIGNATURE =
      new ZipField.F4(0, 0x06064b50, "Zip64 EOCD signature");

  /**
   * Field in the record: the size of the central directory record, not including the first 12
   * bytes of data (the signature and this size information). Therefore this variable should be:
   *
   * <code>size = sizeOfFixedFields + sizeOfVariableData - 12</code>
   *
   * as specified by the zip specification.
   */
  private static final ZipField.F8 F_EOCD_SIZE =
      new ZipField.F8(
          F_SIGNATURE.endOffset(),
          "Zip64 EOCD size",
          new ZipFieldInvariantMinValue(MIN_EOCD_SIZE));

  /** Field in the record: ID program that made the zip (we don't actually use this). */
  private static final ZipField.F2 F_MADE_BY =
      new ZipField.F2(F_EOCD_SIZE.endOffset(), "Made by", new ZipFieldInvariantNonNegative());

  /**
   * Field in the record: Version needed to extract the Zip. We expect this value to be at least
   * {@link CentralDirectoryHeaderCompressInfo#VERSION_WITH_ZIP64_EXTENSIONS}. This value also
   * determines whether we are using Version 1 or Version 2 of the Zip64 EOCD record.
   */
  private static final ZipField.F2 F_VERSION_EXTRACT =
      new ZipField.F2(
          F_MADE_BY.endOffset(),
          "Version to extract",
          new ZipFieldInvariantMinValue(
              CentralDirectoryHeaderCompressInfo.VERSION_WITH_ZIP64_EXTENSIONS));

  /**
   * Field in the record: the number of disk where the Zip64 EOCD is located. It must be zero
   * as multi-file archives are not supported.
   */
  private static final ZipField.F4 F_NUMBER_OF_DISK =
      new ZipField.F4(F_VERSION_EXTRACT.endOffset(), 0, "Number of this disk");

  /**
   * Field in the record: the number of the disk where the central directory resides. This must be
   * zero as multi-file archives are not supported.
   */
  private static final ZipField.F4 F_DISK_CD_START =
      new ZipField.F4(F_NUMBER_OF_DISK.endOffset(), 0, "Disk where CD starts");

  /**
   * Field in the record: the number of entries in the Central Directory on this disk. Because we do
   * not support multi-file archives, this is the same as {@link #F_RECORDS_TOTAL}
   */
  private static final ZipField.F8 F_RECORDS_DISK =
      new ZipField.F8(
          F_DISK_CD_START.endOffset(),
          "Record on disk count",
          new ZipFieldInvariantNonNegative());

  /** Field in the record: the total number of entries in the Central Directory. */
  private static final ZipField.F8 F_RECORDS_TOTAL =
      new ZipField.F8(
          F_RECORDS_DISK.endOffset(),
          "Total records",
          new ZipFieldInvariantNonNegative());

  /** Field in the record: number of bytes of the Central Directory. */
  private static final ZipField.F8 F_CD_SIZE =
      new ZipField.F8(
          F_RECORDS_TOTAL.endOffset(), "Directory size", new ZipFieldInvariantNonNegative());

  /** Field in the record: offset, from the archive start, where the Central Directory starts. */
  private static final ZipField.F8 F_CD_OFFSET =
      new ZipField.F8(
          F_CD_SIZE.endOffset(), "Directory offset", new ZipFieldInvariantNonNegative());

  /**
   * Field in Version 2 of the record: The compression method used for the Central Directory in the
   * given Zip file. Although we do support version 2 of the Zip64 EOCD, we presently do not support
   * any compression method, and thus this value must be zero.
   */
  private static final ZipField.F2 F_V2_CD_COMPRESSION_METHOD =
      new ZipField.F2(
          F_CD_OFFSET.endOffset(), 0, "Version 2: Directory Compression method");

  /**
   * Field in Version 2 of the record: The compressed size of the Central Directory. As Compression
   * is not supported for the CD, this value should always be the same as
   * {@link #F_V2_CD_UNCOMPRESSED_SIZE}.
   */
  private static final ZipField.F8 F_V2_CD_COMPRESSED_SIZE =
      new ZipField.F8(
          F_V2_CD_COMPRESSION_METHOD.endOffset(),
          "Version 2: Directory Compressed Size",
          new ZipFieldInvariantNonNegative());

  /** Field in Version 2 of the record: The uncompressed size of the Central Directory. */
  private static final ZipField.F8 F_V2_CD_UNCOMPRESSED_SIZE =
      new ZipField.F8(
          F_V2_CD_COMPRESSED_SIZE.endOffset(),
          "Version 2: Directory Uncompressed Size",
          new ZipFieldInvariantNonNegative());

  /**
   * Field in Version 2 of the record: The ID for the type of encryption used to encrypt the Central
   * directory. Since Central Directory encryption is not supported, this value has to be zero.
   */
  private static final ZipField.F2 F_V2_CD_ENCRYPTION_ID =
      new ZipField.F2(
          F_V2_CD_UNCOMPRESSED_SIZE.endOffset(),
          0,
          "Version 2: Directory Encryption");

  /**
   * Field in Version 2 of the record: The length of the encryption key for the encryption of the
   * Central Directory given by {@link #F_V2_CD_ENCRYPTION_ID}. Since encryption of the Central
   * Directory is not supported, this value has to be zero.
   */
  private static final ZipField.F2 F_V2_CD_ENCRYPTION_KEY_LENGTH =
      new ZipField.F2(
          F_V2_CD_ENCRYPTION_ID.endOffset(),
          0,
          "Version 2: Directory Encryption key length");

  /**
   * Field in Version 2 of the record: The flags for the encryption method used on the Central
   * Directory. As encryption of the Central Directory is not supported, this value has to be zero.
   */
  private static final ZipField.F2 F_V2_CD_ENCRYPTION_FLAGS =
      new ZipField.F2(
          F_V2_CD_ENCRYPTION_KEY_LENGTH.endOffset(),
          0,
          "Version 2: Directory Encryption Flags");

  /**
   * Field in Version 2 of the record: ID of the algorithm used to hash the Central Directory data.
   * Hashing of the Central Directory is not supported, so this value has to be zero.
   */
  private static final ZipField.F2 F_V2_HASH_ID =
      new ZipField.F2(
          F_V2_CD_ENCRYPTION_FLAGS.endOffset(),
          0,
          "Version 2: Hash algorithm ID");

  /**
   * Field in Version 2 of the record: Length of the data for the hash of the Central Directory.
   * Hashing of the Central Directory is not supported, so this value has to be zero.
   */
  private static final ZipField.F2 F_V2_HASH_LENGTH =
      new ZipField.F2(
          F_V2_HASH_ID.endOffset(),
          0,
          "Version 2: Hash length");

  /** The location of the Zip64 size field relative to the start of the Zip64 EOCD. */
  public static final int SIZE_OFFSET = F_EOCD_SIZE.offset();

  /**
   * The difference between the size in the size field and the true size of the Zip64 EOCD. The size
   * field in the EOCD does not consider the size field and the identifier field when calculating
   * the size of the Zip64 EOCD record.
   */
  public static final int TRUE_SIZE_DIFFERENCE = F_EOCD_SIZE.endOffset();

  /** Code of the program that made the zip. We actually don't care about this. */
  private final long madeBy;

  /** Version needed to extract the zip. */
  private final long versionToExtract;

  /** Number of entries in the Central Directory. */
  private final long totalRecords;

  /** Offset from the beginning of the archive where the Central Directory is located. */
  private final long directoryOffset;

  /** Number of bytes of the Central Directory. */
  private final long directorySize;

  /** The variable extra fields at the end of the Zip64 EOCD (in both Version 1 and 2). */
  private final Zip64ExtensibleDataSector extraFields;

  /** Supplier of the byte representation of the Zip64 EOCD. */
  private final CachedSupplier<byte[]> byteSupplier;

  /**
   * Creates a Zip64Eocd record from the given information from the central directory record.
   *
   * @param totalRecords the number of entries in the central directory.
   * @param directoryOffset the offset of the central directory from the start of the archive.
   * @param directorySize the size (in bytes) of the central directory record.
   * @param useVersion2 whether we want to use Version 2 of the Zip64 EOCD.
   * @param dataSector the extensible data sector.
   */
  Zip64Eocd(
      long totalRecords,
      long directoryOffset,
      long directorySize,
      boolean useVersion2,
      Zip64ExtensibleDataSector dataSector) {
    this.madeBy = DEFAULT_VERSION_MADE_BY;
    this.totalRecords = totalRecords;
    this.directorySize = directorySize;
    this.directoryOffset = directoryOffset;
    this.versionToExtract =
        useVersion2
            ? CentralDirectoryHeaderCompressInfo.VERSION_WITH_CENTRAL_FILE_ENCRYPTION
            : CentralDirectoryHeaderCompressInfo.VERSION_WITH_ZIP64_EXTENSIONS;
    extraFields = dataSector;

    byteSupplier = new CachedSupplier<>(this::computeByteRepresentation);
  }

  /**
   * Creates a Zip64 EOCD from the given byte information. It does verify that the record starts
   * with the correct header information.
   *
   * @param bytes the bytes to be read as a Zip64 EOCD
   * @throws IOException the bytes could not be read as a Zip64 EOCD
   */
  Zip64Eocd(ByteBuffer bytes) throws IOException {

    F_SIGNATURE.verify(bytes);
    long eocdSize = F_EOCD_SIZE.read(bytes);
    long madeBy = F_MADE_BY.read(bytes);
    long versionToExtract = F_VERSION_EXTRACT.read(bytes);
    F_NUMBER_OF_DISK.verify(bytes);
    F_DISK_CD_START.verify(bytes);
    long totalRecords1 = F_RECORDS_DISK.read(bytes);
    long totalRecords2 = F_RECORDS_TOTAL.read(bytes);
    long directorySize = F_CD_SIZE.read(bytes);
    long directoryOffset = F_CD_OFFSET.read(bytes);
    long sizeOfFixedFields = F_CD_OFFSET.endOffset();

    // sanity checks for Version 1 fields.
    if (totalRecords1 != totalRecords2) {
      throw new IOException(
          "Zip states records split in multiple disks, which is not supported");
    }

    // read Version 2 fields if necessary
    if (versionToExtract
        >= CentralDirectoryHeaderCompressInfo.VERSION_WITH_CENTRAL_FILE_ENCRYPTION) {
      if (eocdSize < F_V2_HASH_LENGTH.endOffset() - F_EOCD_SIZE.endOffset()) {
        throw new IOException(
            "Zip states the size of Zip64 EOCD is too small for version 2 format.");
      }

      F_V2_CD_COMPRESSION_METHOD.verify(bytes);
      long compressedSize = F_V2_CD_COMPRESSED_SIZE.read(bytes);
      long uncompressedSize = F_V2_CD_UNCOMPRESSED_SIZE.read(bytes);
      F_V2_CD_ENCRYPTION_ID.verify(bytes);
      F_V2_CD_ENCRYPTION_KEY_LENGTH.verify(bytes);
      F_V2_CD_ENCRYPTION_FLAGS.verify(bytes);
      F_V2_HASH_ID.verify(bytes);
      F_V2_HASH_LENGTH.verify(bytes);
      sizeOfFixedFields = F_V2_HASH_LENGTH.endOffset();

      // sanity checks for version 2 fields.
      if (compressedSize != uncompressedSize) {
        throw new IOException(
            "Zip states Central Directory Compression is used, which is not supported");
      }
      directorySize = uncompressedSize;
    }

    this.madeBy = madeBy;
    this.versionToExtract = versionToExtract;
    this.totalRecords = totalRecords1;
    this.directorySize = directorySize;
    this.directoryOffset = directoryOffset;

    long extensibleDataSize = eocdSize - (sizeOfFixedFields - F_EOCD_SIZE.endOffset());

    if (extensibleDataSize > Integer.MAX_VALUE) {
      throw new IOException("Extensible data of size: " + extensibleDataSize + "not supported");
    }
    byte[] rawData = new byte[Ints.checkedCast(extensibleDataSize)];
    bytes.get(rawData);
    extraFields = new Zip64ExtensibleDataSector(rawData);
    byteSupplier = new CachedSupplier<>(this::computeByteRepresentation);
  }

  /**
   * The size of the fixed field in the Zip64 EOCD. This vaue may be different if we are using a
   * version 1 or version 2 record.
   *
   * @return the size of the fixed fields.
   */
  private int sizeOfFixedFields() {
    return versionToExtract
        >= CentralDirectoryHeaderCompressInfo.VERSION_WITH_CENTRAL_FILE_ENCRYPTION
          ? F_V2_HASH_LENGTH.endOffset()
          : F_CD_OFFSET.endOffset();
  }

  /**
   * Gets the size (in bytes) of the Zip64 EOCD record.
   *
   * @return the size of the record.
   */
  public int size() {
    return sizeOfFixedFields() + extraFields.size();
  }

  public long getTotalRecords() {
    return totalRecords;
  }

  public long getDirectorySize() {
    return directorySize;
  }

  public long getDirectoryOffset() {
    return directoryOffset;
  }

  public Zip64ExtensibleDataSector getExtraFields() {
    return extraFields;
  }

  public long getVersionToExtract() { return versionToExtract; }

  /**
   * Gets the byte representation of The Zip64 EOCD record.
   *
   * @return the bytes of the EOCD.
   */
  public byte[] toBytes() {
    return byteSupplier.get();
  }

  private byte[] computeByteRepresentation() {
    int size = size();
    ByteBuffer out = ByteBuffer.allocate(size);

    try {
      F_SIGNATURE.write(out);
      F_EOCD_SIZE.write(out, size - F_EOCD_SIZE.endOffset());
      F_MADE_BY.write(out, madeBy);
      F_VERSION_EXTRACT.write(out, versionToExtract);
      F_NUMBER_OF_DISK.write(out);
      F_DISK_CD_START.write(out);
      F_RECORDS_DISK.write(out, totalRecords);
      F_RECORDS_TOTAL.write(out, totalRecords);
      F_CD_SIZE.write(out, directorySize);
      F_CD_OFFSET.write(out, directoryOffset);

      // write version 2 fields if necessary.
      if (versionToExtract
          >= CentralDirectoryHeaderCompressInfo.VERSION_WITH_CENTRAL_FILE_ENCRYPTION) {
        F_V2_CD_COMPRESSION_METHOD.write(out);
        F_V2_CD_COMPRESSED_SIZE.write(out, directorySize);
        F_V2_CD_UNCOMPRESSED_SIZE.write(out, directorySize);
        F_V2_CD_ENCRYPTION_ID.write(out);
        F_V2_CD_ENCRYPTION_KEY_LENGTH.write(out);
        F_V2_CD_ENCRYPTION_FLAGS.write(out);
        F_V2_HASH_ID.write(out);
        F_V2_HASH_LENGTH.write(out);
      }

      extraFields.write(out);

      return out.array();
    } catch (IOException e) {
      throw new IOExceptionWrapper(e);
    }
  }
}
