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

import com.android.tools.build.apkzlib.utils.IOExceptionWrapper;
import com.android.tools.build.apkzlib.zip.utils.LittleEndianUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;

/**
 * The collection of all data stored in all End of Central Directory records in the zip file. The
 * {@code EOCDGroup} is meant to collect and manage all the information about the {@link Eocd},
 * {@link Zip64EocdLocator}, and the {@link Zip64Eocd} in one place.
 */
public class EocdGroup {

  /** Minimum size the EOCD can have. */
  private static final int MIN_EOCD_SIZE = 22;

  /** Maximum size for the EOCD. */
  private static final int MAX_EOCD_COMMENT_SIZE = 65535;

  /** How many bytes to look back from the end of the file to look for the EOCD signature. */
  private static final int LAST_BYTES_TO_READ = MIN_EOCD_SIZE + MAX_EOCD_COMMENT_SIZE;

  /** Signature of the Zip64 EOCD locator record. */
  private static final int ZIP64_EOCD_LOCATOR_SIGNATURE = 0x07064b50;

  /** Signature of the EOCD record. */
  private static final long EOCD_SIGNATURE = 0x06054b50;

  /**
   * The EOCD entry. Will be {@code null} if there is no EOCD (because the zip is new) or the one
   * that exists on disk is no longer valid (because the zip has been changed).
   *
   * <p>If the EOCD is deleted because the zip has been changed and the old EOCD was no longer
   * valid, then {@link #eocdComment} will contain the comment saved from the EOCD.
   */
  @Nullable
  private FileUseMapEntry<Eocd> eocdEntry;

  /**
   * The EOCD locator entry. Will be {@code null} if there is no EOCD (because the zip is new),
   * the EOCD on disk is no longer valid (because the zip has been changed), or the zip file is not
   * in Zip64 format (There are no values in the EOCD that overflow or any files with Zip64
   * extended information.)
   *
   * <p> If this value is {@code nonnull} then the EOCD exists and is in Zip64 format (<i>i.e.</i>
   * both {@link #eocdEntry} and {@link #eocd64Entry} will be {@code nonnull}).
   */
  @Nullable
  private FileUseMapEntry<Zip64EocdLocator> eocd64Locator;

  /**
   * The Zip64 EOCD entry. Will be {@code null} if there is no EOCD (because the zip is new),
   * the EOCD on disk is no longer valid (because the zip has been changed), or the zip file is not
   * in Zip64 format (There are no values in the EOCD that overflow or any files with Zip64
   * extended information.)
   *
   * <p> If this value is {@code nonnull} then the EOCD exists and is in Zip64 format (<i>i.e.</i>
   * both {@link #eocdEntry} and {@link #eocd64Locator} will be {@code nonnull}).
   */
  @Nullable
  private FileUseMapEntry<Zip64Eocd> eocd64Entry;

  /**
   * This field contains the comment in the zip's EOCD if there is no in-memory EOCD structure. This
   * may happen, for example, if the zip has been changed and the Central Directory and EOCD have
   * been deleted (in-memory). In that case, this field will save the comment to place on the EOCD
   * once it is created.
   *
   * <p>This field will only be non-{@code null} if there is no in-memory EOCD structure
   * (<i>i.e.</i>, {@link #eocdEntry} is {@code null}, If there is an {@link #eocdEntry}, then the
   * comment will be there instead of being in this field.
   */
  @Nullable
  private byte[] eocdComment;

  /**
   * This field contains the extensible data sector in the zip's Zip64 EOCD if there is no EOCD
   * in-memory. This may happen if the zip has been modified and the Central Directory and EOCD have
   * been deleted (in-memory). In that case, this field will save the data sector to place in the
   * Zip64 EOCD once it is created.
   *
   * <p>This field will only be non-{@code null} if there is no in-memory EOCD structure
   * (<i>i.e.</i>, {@link #eocdEntry} is {@code null}, If there is an {@link #eocdEntry}, then the
   * data sector will be in the {@link #eocd64Entry} instead of being in this field.
   */
  @Nullable
  private Zip64ExtensibleDataSector eocdDataSector;

  /**
   * Specifies whether the Zip64 Eocd will be in Version 2 or Version 1 format when it is
   * constructed.
   */
  private boolean useVersion2Header;

  /** The zip file to which this EOCD record belongs. */
  private final ZFile file;

  /** The in-memory map of the pieces of the zip-file. */
  private final FileUseMap map;

  /** The zip file's log. */
  private final VerifyLog verifyLog;

  /**
   * Constructs an empty EOCD group, which will have no in-memory EOCD structure.
   *
   * @param file The zip file to which this EOCD record belongs.
   * @param map he in-memory map of the zip file.
   */
  EocdGroup(ZFile file, FileUseMap map) {

    eocd64Entry = null;
    eocd64Locator = null;
    eocdEntry = null;
    eocdComment = new byte[0];
    eocdDataSector = new Zip64ExtensibleDataSector();
    this.file = file;
    this.map = map;
    this.verifyLog = file.getVerifyLog();
    useVersion2Header = false;
  }

  /**
   * Attempts to read the EOCD record into the {@link EocdGroup} from disk specified by
   * {@link #file}. It will populate the in-memory EOCD structure (<i>i.e.</i> {@link #eocdEntry}),
   * including the Zip64 EOCD record and locator if applicable.
   *
   * @param fileLength The length of the file on disk, used to help find the EOCD record.
   * @throws IOException Failed to read the EOCD.
   */
  void readRecord(long fileLength) throws IOException {
    /*
     * Read the last part of the zip into memory. If we don't find the EOCD signature by then,
     * the file is corrupt.
     */
    int lastToRead = LAST_BYTES_TO_READ;
    if (lastToRead > fileLength) {
      lastToRead = Ints.checkedCast(fileLength);
    }

    byte[] last = new byte[lastToRead];
    file.directFullyRead(fileLength - lastToRead, last);

    /*
     * Start endIdx at the first possible location where the signature can be located and then
     * move backwards. Because the EOCD must have at least MIN_EOCD size, the first byte of the
     * signature (and first byte of the EOCD) must be located at last.length - MIN_EOCD_SIZE.
     *
     * Because the EOCD signature may exist in the file comment, when we find a signature we
     * will try to read the Eocd. If we fail, we continue searching for the signature. However,
     * we will keep the last exception in case we don't find any signature.
     */
    Eocd eocd = null;
    int foundEocdSignatureIdx = -1;
    IOException errorFindingSignature = null;
    long eocdStart = -1;

    for (int endIdx = last.length - MIN_EOCD_SIZE;
        endIdx >= 0 && foundEocdSignatureIdx == -1;
        endIdx--) {

      ByteBuffer potentialLocator = ByteBuffer.wrap(last, endIdx, 4);
      if (LittleEndianUtils.readUnsigned4Le(potentialLocator) == EOCD_SIGNATURE) {

        /*
         * We found a signature. Try to read the EOCD record.
         */

        foundEocdSignatureIdx = endIdx;
        ByteBuffer eocdBytes =
            ByteBuffer.wrap(last, foundEocdSignatureIdx, last.length - foundEocdSignatureIdx);

        try {
          eocd = new Eocd(eocdBytes);

          eocdStart = fileLength - lastToRead + foundEocdSignatureIdx;

          /*
           * Make sure the EOCD takes the whole file up to the end. Log an error if it
           * doesn't.
           */
          if (eocdStart + eocd.getEocdSize() != fileLength) {
            verifyLog.log(
                "EOCD starts at "
                    + eocdStart
                    + " and has "
                    + eocd.getEocdSize()
                    + " bytes, but file ends at "
                    + fileLength
                    + ".");
          }
        } catch (IOException e) {
          if (errorFindingSignature != null) {
            e.addSuppressed(errorFindingSignature);
          }

          errorFindingSignature = e;
          foundEocdSignatureIdx = -1;
          eocd = null;
        }
      }
    }

    if (foundEocdSignatureIdx == -1) {
      throw new IOException(
          "EOCD signature not found in the last " + lastToRead + " bytes of the file.",
          errorFindingSignature);
    }

    Verify.verify(eocdStart >= 0);
    eocdEntry = map.add(eocdStart, eocdStart + eocd.getEocdSize(), eocd);

    /*
     * Look for the Zip64 central directory locator. If we find it, then this file is a Zip64
     * file and we need to read both the Zip64 EOCD locator and Zip64 EOCD
     */
    long zip64LocatorStart = eocdStart - Zip64EocdLocator.LOCATOR_SIZE;
    if (zip64LocatorStart >= 0) {
      byte[] possibleZip64Locator = new byte[Zip64EocdLocator.LOCATOR_SIZE];
      file.directFullyRead(zip64LocatorStart, possibleZip64Locator);
      if (LittleEndianUtils.readUnsigned4Le(ByteBuffer.wrap(possibleZip64Locator))
          == ZIP64_EOCD_LOCATOR_SIGNATURE) {

        /* found the locator. Read it into memory. */

        Zip64EocdLocator locator = new Zip64EocdLocator(ByteBuffer.wrap(possibleZip64Locator));
        eocd64Locator = map.add(
            zip64LocatorStart, zip64LocatorStart + locator.getSize(), locator);

        /* Find the size of the Zip64 EOCD by reading its size field */
        byte[] zip64EocdSizeHolder = new byte[8];
        file.directFullyRead(
            locator.getZ64EocdOffset() + Zip64Eocd.SIZE_OFFSET, zip64EocdSizeHolder);
        long zip64EocdSize =
            LittleEndianUtils.readUnsigned8Le(ByteBuffer.wrap(zip64EocdSizeHolder))
                + Zip64Eocd.TRUE_SIZE_DIFFERENCE;

        /* read the Zip64 EOCD into memory */

        byte[] zip64EocdBytes = new byte[Ints.checkedCast(zip64EocdSize)];
        file.directFullyRead(locator.getZ64EocdOffset(), zip64EocdBytes);
        Zip64Eocd zip64Eocd = new Zip64Eocd(ByteBuffer.wrap(zip64EocdBytes));
        useVersion2Header =
            zip64Eocd.getVersionToExtract()
                >= CentralDirectoryHeaderCompressInfo.VERSION_WITH_CENTRAL_FILE_ENCRYPTION;

        long zip64EocdEnd = locator.getZ64EocdOffset() + zip64EocdSize;
        if (zip64EocdEnd != zip64LocatorStart) {
          String msg =
              "Zip64 EOCD record is stored in ["
                  + locator.getZ64EocdOffset()
                  + " - "
                  + zip64EocdEnd
                  + "] and EOCD starts at "
                  + zip64LocatorStart
                  + ".";

          /*
           * If there is an empty space between the Zip64 EOCD and the EOCD locator, we proceed
           * logging an error. If the Zip64 EOCD ends after the start of the EOCD locator (and
           * therefore, they overlap), throw an exception.
           */
          if (zip64EocdEnd > zip64LocatorStart) {
            throw new IOException(msg);
          } else {
            verifyLog.log(msg);
          }
        }

        eocd64Entry = map.add(
            locator.getZ64EocdOffset(), zip64EocdEnd, zip64Eocd);
      }
    }

  }

  /**
   * Computes the EOCD record from the given Central Directory entry in memory. This will populate
   * the EOCD in-memory and possibly the Zip64 EOCD and Locator if applicable.
   *
   * @param directoryEntry The entry to create the EOCD record from.
   * @param extraDirectoryOffset The offset between the last local entry and the Central Directory.
   * This will be preserved by the EOCD if the Central Directory is empty.
   * @throws IOException Failed to create the EOCD record.
   */
  void computeRecord(
      @Nullable FileUseMapEntry<CentralDirectory> directoryEntry,
      long extraDirectoryOffset) throws IOException {

    long dirStart;
    long dirSize;
    long dirNumEntries;

    if (directoryEntry != null) {
      dirStart = directoryEntry.getStart();
      dirSize = directoryEntry.getSize();
      dirNumEntries = directoryEntry.getStore().getEntries().size();
    } else {
      // if we do not have a directory, then we must leave any required offset.
      dirStart = extraDirectoryOffset;
      dirSize = 0;
      dirNumEntries = 0;
    }

    /*
     * We need a Zip64 EOCD if any value overflows or if Zip64 file extensions are used as stated
     * in the Zip Specification.
     */

    boolean useZip64Eocd =
        dirStart > Eocd.MAX_CD_OFFSET ||
            dirSize > Eocd.MAX_CD_SIZE ||
            dirNumEntries > Eocd.MAX_TOTAL_RECORDS ||
            (directoryEntry != null && directoryEntry.getStore().containsZip64Files());

    /* construct the Zip64 EOCD and locator first, as they come before the standard EOCD */
    if (useZip64Eocd) {
      Verify.verify(eocdDataSector != null);
      Zip64Eocd zip64Eocd =
          new Zip64Eocd(dirNumEntries, dirStart, dirSize, useVersion2Header, eocdDataSector);
      eocdDataSector = null;
      byte[] zip64EocdBytes = zip64Eocd.toBytes();
      long zip64Offset = map.size();
      map.extend(zip64Offset + zip64EocdBytes.length);
      eocd64Entry = map.add(zip64Offset, zip64Offset + zip64EocdBytes.length, zip64Eocd);

      Zip64EocdLocator locator = new Zip64EocdLocator(eocd64Entry.getStart());
      byte[] locatorBytes = locator.toBytes();
      long locatorOffset = map.size();
      map.extend(locatorOffset + locatorBytes.length);
      eocd64Locator = map.add(locatorOffset, locatorOffset + locatorBytes.length, locator);
    }

    /* add the EOCD to the end of the file */

    Verify.verify(eocdComment != null);
    Eocd eocd = new Eocd(
        Math.min(dirNumEntries, Eocd.MAX_TOTAL_RECORDS),
        Math.min(dirStart, Eocd.MAX_CD_OFFSET),
        Math.min(dirSize, Eocd.MAX_CD_SIZE),
        eocdComment);
    eocdComment = null;
    byte[] eocdBytes = eocd.toBytes();
    long eocdOffset = map.size();
    map.extend(eocdOffset + eocdBytes.length);
    eocdEntry = map.add(eocdOffset, eocdOffset + eocdBytes.length, eocd);
  }

  /**
   * Writes the entire EOCD record to the end of the file. The EOCDGroup must <i>not</i> be empty
   * ({@link #isEmpty()}) by being populated by a call to
   * {@link #computeRecord(FileUseMapEntry, long)}, and the Central Directory must already be
   * written to the file. If the CentralDirectory has not written, then {@link #file} should have
   * no entries.
   *
   * @throws IOException Failed to write the EOCD record.
   */
  void appendToFile() throws IOException {
    Preconditions.checkNotNull(eocdEntry, "eocdEntry == null");

    if (eocd64Entry != null) {
      Zip64Eocd zip64Eocd = eocd64Entry.getStore();
      Preconditions.checkNotNull(zip64Eocd);
      Zip64EocdLocator locator = eocd64Locator.getStore();
      Preconditions.checkNotNull(locator);

      file.directWrite(eocd64Entry.getStart(), zip64Eocd.toBytes());
      file.directWrite(eocd64Locator.getStart(), locator.toBytes());
    }

    Eocd eocd = eocdEntry.getStore();
    Preconditions.checkNotNull(eocd, "eocd == null");

    byte[] eocdBytes = eocd.toBytes();
    long eocdOffset = eocdEntry.getStart();

    file.directWrite(eocdOffset, eocdBytes);
  }

  /**
   * Obtains the byte array representation of the EOCD. The EOCD must have already been computed for
   * this method to be invoked.
   *
   * @return The byte representation of the EOCD.
   * @throws IOException Failed to obtain the byte representation of the EOCD.
   */
  byte[] getEocdBytes() throws IOException {
    Preconditions.checkNotNull(eocdEntry, "eocdEntry == null");

    Eocd eocd = eocdEntry.getStore();
    Preconditions.checkNotNull(eocd, "eocd == null");

    return eocd.toBytes();
  }

  /**
   * Obtains the byte array representation of the Zip64 EOCD Locator. The EOCD record must already
   * have been computed for this method to be invoked.
   *
   * @return The byte representation of the Zip64 EOCD Locator, or null if the EOCD record is not
   * in Zip64 format.
   * @throws IOException Failed to obtain the byte representation of the EOCD Locator.
   */
  @VisibleForTesting
  @Nullable
  byte[] getEocdLocatorBytes() throws IOException {
    Preconditions.checkNotNull(eocdEntry);

    if (eocd64Locator == null) {
      return null;
    }

    return eocd64Locator.getStore().toBytes();
  }

  /**
   * Obtains the byte array representation of the Zip64 EOCD. The EOCD record must already
   * have been computed for this method to be invoked.
   *
   * @return The byte representation of the Zip64 EOCD, or null if the EOCD record is not
   * in Zip64 format.
   * @throws IOException Failed to obtain the byte representation of the Zip64 EOCD.
   */
  @VisibleForTesting
  @Nullable
  byte[] getZ64EocdBytes() throws IOException {
    Preconditions.checkNotNull(eocdEntry);

    if (eocd64Entry == null) {
      return null;
    }

    return eocd64Entry.getStore().toBytes();
  }

  /**
   * Checks whether the EOCD record is presently in-memory. (<i>i.e.</i> the EOCD was either read
   * from disk and is still valid, or has been computed from the Central Directory).
   *
   * @return True iff the EOCD record is in-memory.
   */
  boolean isEmpty() {
    return eocdEntry == null;
  }

  /**
   * Sets whether or not the EOCD record should use the Version 1 or Version 2 of the Zip64 EOCD
   * (iff the file needs a Zip64 record). The EOCD record should not be in-memory when trying to set
   * this value, and the EOCD will need to be recomputed to have any affect.
   *
   * @param useVersion2Header True if the Version 2 header is to be used, and false for the Version
   * 1 header.
   */
  void setUseVersion2Header(boolean useVersion2Header) {
    verifyLog.verify(eocdEntry == null, "eocdEntry != null");

    this.useVersion2Header = useVersion2Header;
  }

  /**
   * Specifies if the EOCD Group will be using a Version 2 Zip64 EOCD record or a Version 1 record
   * if the file needs to be in Zip64 format.
   *
   * @return True if the Version 2 record will be used, and false if the Version 1 record will be
   * used.
   */
  boolean usingVersion2Header() {
    return useVersion2Header;
  }

  /**
   * Removes the EOCD record from memory.
   */
  void deleteRecord() {
    if (eocdEntry != null) {
      map.remove(eocdEntry);

      Eocd eocd = eocdEntry.getStore();
      Verify.verify(eocd != null);
      eocdComment = eocd.getComment();
      eocdEntry = null;
    }

    if (eocd64Locator != null) {
      Verify.verify(eocd64Entry != null);
      eocdDataSector = eocd64Entry.getStore().getExtraFields();
      map.remove(eocd64Locator);
      map.remove(eocd64Entry);
      eocd64Locator = null;
      eocd64Entry = null;
    } else {
      eocdDataSector = new Zip64ExtensibleDataSector();
    }
  }

  /**
   * Sets the EOCD comment.
   *
   * @param comment The new comment; no conversion is done, these exact bytes will be placed in the
   *     EOCD comment.
   * @throws IllegalArgumentException If the comment corrupts the ZipFile by having a valid EOCD
   *     record in it.
   */
  void setEocdComment(byte[] comment) {
    if (comment.length > MAX_EOCD_COMMENT_SIZE) {
      throw new IllegalArgumentException(
          "EOCD comment size ("
              + comment.length
              + ") is larger than the maximum allowed ("
              + MAX_EOCD_COMMENT_SIZE
              + ")");
    }

    // Check if the EOCD signature appears anywhere in the comment we need to check if it
    // is valid.
    for (int i = 0; i < comment.length - MIN_EOCD_SIZE; i++) {
      // Remember: little endian...
      ByteBuffer potentialSignature = ByteBuffer.wrap(comment, i, 4);
      try {
        if (LittleEndianUtils.readUnsigned4Le(potentialSignature) == EOCD_SIGNATURE) {
          // We found a possible EOCD signature at position i. Try to read it.
          ByteBuffer bytes = ByteBuffer.wrap(comment, i, comment.length - i);
          try {
            new Eocd(bytes);
            // If a valid record is found in the comment then this corrupts the Zip file record
            // as we look for the EOCD at the back of the file (where the comment is) first.
            throw new IllegalArgumentException(
                "Position " + i + " of the comment contains a valid EOCD record.");
          } catch (IOException e) {
            // Fine, this is an invalid record. Move along...
          }
        }
      } catch (IOException e) {
        throw new IOExceptionWrapper(e);
      }
    }

    deleteRecord();
    eocdComment = new byte[comment.length];
    System.arraycopy(comment, 0, eocdComment, 0, comment.length);
  }

  /**
   * Returns the start of the EOCD record location in the file or -1 if the EOCD is not in memory.
   *
   * @return The start of the record.
   */
  long getOffset() {
    if (eocdEntry == null) {
      return -1;
    }
    return getRecordStart();
  }

  /**
   * Gets the comment in the EOCD.
   *
   * @return The comment exactly as it was encoded in the EOCD, no encoding is done.
   */
  byte[] getEocdComment() {
    if (eocdEntry == null) {
      Verify.verify(eocdComment != null);
      byte[] eocdCommentCopy = eocdComment.clone();
      return eocdCommentCopy;
    }

    Eocd eocd = eocdEntry.getStore();
    Verify.verify(eocd != null);
    return eocd.getComment();
  }

  /**
   * Gets the size of the central directory as specified from the EOCD record. The EOCD must be in
   * memory before this method is invoked.
   *
   * @return The directory's size.
   */
  long getDirectorySize() {
    Preconditions.checkNotNull(eocdEntry, "eocdEntry == null");

    Eocd eocd = eocdEntry.getStore();

    if (eocd64Entry != null && eocd.getDirectorySize() == Eocd.MAX_CD_SIZE) {
      return eocd64Entry.getStore().getDirectorySize();
    } else {
      return eocd.getDirectorySize();
    }
  }

  /**
   * Gets the offset of the Central Directory from the start of the archive as specified from the
   * EOCD record. The EOCD must be in memory before this method is invoked.
   *
   * @return The offset of the start of the Central Directory.
   */
  long getDirectoryOffset() {
    Preconditions.checkNotNull(eocdEntry, "eocdEntry == null");

    Eocd eocd = eocdEntry.getStore();

    if (eocd64Entry != null && eocd.getDirectoryOffset() == Eocd.MAX_CD_OFFSET) {
      return eocd64Entry.getStore().getDirectoryOffset();
    } else {
      return eocd.getDirectoryOffset();
    }
  }

  /**
   * Gets the total number of entries in the Central Directory as specified from the EOCD record.
   * The EOCD must be in memory before this method is invoked.
   *
   * @return The total number of records in the Central Directory.
   */
  long getTotalDirectoryRecords() {
    Preconditions.checkNotNull(eocdEntry, "eocdEntry == null");

    Eocd eocd = eocdEntry.getStore();
    if (eocd64Entry != null && eocd.getTotalRecords() == Eocd.MAX_TOTAL_RECORDS) {
      return eocd64Entry.getStore().getTotalRecords();
    }

    return eocd.getTotalRecords();
  }

  /**
   * Returns the start of the EOCD record from the start of the archive. This will be the same as
   * the start of the standard EOCD in a Zip32 file or in a Zip64 file will be the start of the
   * Zip64 Eocd record. The EOCD must be in memory for this method to be invoked.
   *
   * @return The start of the entire EOCD record.
   */
  long getRecordStart() {
    Verify.verify(eocdEntry != null, "eocdEntry == null");
    if (eocd64Entry != null) {
      return eocd64Entry.getStart();
    }
    return eocdEntry.getStart();
  }

  /**
   * Returns the total size of the EOCD record. This will be the same as the standard EOCD size for
   * a Zip32 file or in a Zip64 file will be the start of the Zip64 record to the end of the
   * standard EOCD. the EOCD must be in memory for this method to be invoked.
   *
   * @return The total size of the EOCD record.
   */
  public long getRecordSize() {
    if (eocd64Entry != null) {
      Verify.verify(eocdEntry != null);
      return eocdEntry.getEnd() - eocd64Entry.getStart();
    }
    if (eocdEntry == null) {
      return -1;
    }

    return eocdEntry.getSize();
  }

  /**
   * Returns the Zip64 Extensible Data Sector, or {@code null} if the EOCD record is not in the
   * Zip64 format. The EOCD must be in memory for this method to be invoked.
   *
   * @return The Extensible data sector, or {@code null} if none exists.
   */
  @Nullable
  public Zip64ExtensibleDataSector getExtensibleData() {
    Verify.verify(eocdEntry != null);
    if (eocd64Entry != null) {
      return eocd64Entry.getStore().getExtraFields();
    }

    return null;
  }
}
