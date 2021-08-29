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

import com.android.tools.build.apkzlib.bytestorage.ByteStorage;
import com.android.tools.build.apkzlib.bytestorage.CloseableByteSourceFromOutputStreamBuilder;
import com.android.tools.build.apkzlib.utils.IOExceptionWrapper;
import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Verify;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Comparator;
import javax.annotation.Nullable;

/**
 * A stored entry represents a file in the zip. The entry may or may not be written to the zip file.
 *
 * <p>Stored entries provide the operations that are related to the files themselves, not to the
 * zip. It is through the {@code StoredEntry} class that entries can be deleted ({@link #delete()},
 * open ({@link #open()}) or realigned ({@link #realign()}).
 *
 * <p>Entries are not created directly. They are created using {@link ZFile#add(String, InputStream,
 * boolean)} and obtained from the zip file using {@link ZFile#get(String)} or {@link
 * ZFile#entries()}.
 *
 * <p>Most of the data in the an entry is in the Central Directory Header. This includes the name,
 * compression method, file compressed and uncompressed sizes, CRC32 checksum, etc. The CDH can be
 * obtained using the {@link #getCentralDirectoryHeader()} method.
 */
public class StoredEntry {

  /** Comparator that compares instances of {@link StoredEntry} by their names. */
  static final Comparator<StoredEntry> COMPARE_BY_NAME =
      (o1, o2) -> {
        if (o1 == null && o2 == null) {
          return 0;
        }

        if (o1 == null) {
          return -1;
        }

        if (o2 == null) {
          return 1;
        }

        String name1 = o1.getCentralDirectoryHeader().getName();
        String name2 = o2.getCentralDirectoryHeader().getName();
        return name1.compareTo(name2);
      };

  /** Signature of the data descriptor. */
  private static final int DATA_DESC_SIGNATURE = 0x08074b50;

  /** Local header field: signature. */
  private static final ZipField.F4 F_LOCAL_SIGNATURE = new ZipField.F4(0, 0x04034b50, "Signature");

  /** Local header field: version to extract, should match the CDH's. */
  @VisibleForTesting
  static final ZipField.F2 F_VERSION_EXTRACT =
      new ZipField.F2(
          F_LOCAL_SIGNATURE.endOffset(), "Version to extract", new ZipFieldInvariantNonNegative());

  /** Local header field: GP bit flag, should match the CDH's. */
  private static final ZipField.F2 F_GP_BIT =
      new ZipField.F2(F_VERSION_EXTRACT.endOffset(), "GP bit flag");

  /** Local header field: compression method, should match the CDH's. */
  private static final ZipField.F2 F_METHOD =
      new ZipField.F2(
          F_GP_BIT.endOffset(), "Compression method", new ZipFieldInvariantNonNegative());

  /** Local header field: last modification time, should match the CDH's. */
  private static final ZipField.F2 F_LAST_MOD_TIME =
      new ZipField.F2(F_METHOD.endOffset(), "Last modification time");

  /** Local header field: last modification time, should match the CDH's. */
  private static final ZipField.F2 F_LAST_MOD_DATE =
      new ZipField.F2(F_LAST_MOD_TIME.endOffset(), "Last modification date");

  /** Local header field: CRC32 checksum, should match the CDH's. 0 if there is no data. */
  private static final ZipField.F4 F_CRC32 = new ZipField.F4(F_LAST_MOD_DATE.endOffset(), "CRC32");

  /** Local header field: compressed size, size the data takes in the zip file. */
  private static final ZipField.F4 F_COMPRESSED_SIZE =
      new ZipField.F4(F_CRC32.endOffset(), "Compressed size", new ZipFieldInvariantNonNegative());

  /** Local header field: uncompressed size, size the data takes after extraction. */
  private static final ZipField.F4 F_UNCOMPRESSED_SIZE =
      new ZipField.F4(
          F_COMPRESSED_SIZE.endOffset(), "Uncompressed size", new ZipFieldInvariantNonNegative());

  /** Local header field: length of the file name. */
  private static final ZipField.F2 F_FILE_NAME_LENGTH =
      new ZipField.F2(
          F_UNCOMPRESSED_SIZE.endOffset(), "@File name length", new ZipFieldInvariantNonNegative());

  /** Local header filed: length of the extra field. */
  private static final ZipField.F2 F_EXTRA_LENGTH =
      new ZipField.F2(
          F_FILE_NAME_LENGTH.endOffset(), "Extra length", new ZipFieldInvariantNonNegative());

  /** Local header size (fixed part, not counting file name or extra field). */
  static final int FIXED_LOCAL_FILE_HEADER_SIZE = F_EXTRA_LENGTH.endOffset();

  /** Type of entry. */
  private final StoredEntryType type;

  /** The central directory header with information about the file. */
  private final CentralDirectoryHeader cdh;

  /** The file this entry is associated with */
  private final ZFile file;

  /** Has this entry been deleted? */
  private boolean deleted;

  /** Extra field specified in the local directory. */
  private ExtraField localExtra;

  /** Type of data descriptor associated with the entry. */
  private Supplier<DataDescriptorType> dataDescriptorType;

  /**
   * Source for this entry's data. If this entry is a directory, this source has to have zero size.
   */
  private ProcessedAndRawByteSources source;

  /** Verify log for the entry. */
  private final VerifyLog verifyLog;

  /** Storage used to create buffers when loading storage into memory. */
  private final ByteStorage storage;

  /** Entry it is linking to. */
  private final StoredEntry linkedEntry;

  /** Offset of the nested link. */
  private final long nestedOffset;

  /** Dummy entry won't be written to file. */
  private final boolean dummy;

  /**
   * Creates a new stored entry.
   *
   * @param header the header with the entry information; if the header does not contain an offset
   *     it means that this entry is not yet written in the zip file
   * @param file the zip file containing the entry
   * @param source the entry's data source; it can be {@code null} only if the source can be read
   *     from the zip file, that is, if {@code header.getOffset()} is non-negative
   * @throws IOException failed to create the entry
   */
  StoredEntry(
          CentralDirectoryHeader header,
          ZFile file,
          @Nullable ProcessedAndRawByteSources source,
          ByteStorage storage)
          throws IOException {
      this(header, file, source, storage, null, 0, false);
  }

  StoredEntry(
          String name,
          ZFile file,
          ByteStorage storage,
          StoredEntry linkedEntry,
          StoredEntry nestedEntry,
          long nestedOffset,
          boolean dummy)
          throws IOException {
      this((nestedEntry == null ? linkedEntry: nestedEntry).linkingCentralDirectoryHeader(name, file),
              file, (nestedEntry == null ? linkedEntry : nestedEntry).getSource(), storage, linkedEntry, nestedOffset, dummy);
  }

  private CentralDirectoryHeader linkingCentralDirectoryHeader(String name, ZFile file) {
    boolean encodeWithUtf8 = !EncodeUtils.canAsciiEncode(name);
    GPFlags flags = GPFlags.make(encodeWithUtf8);
    return cdh.link(name, EncodeUtils.encode(name, flags), flags, file);
  }

  private StoredEntry(
      CentralDirectoryHeader header,
      ZFile file,
      @Nullable ProcessedAndRawByteSources source,
      ByteStorage storage,
      StoredEntry linkedEntry,
      long nestedOffset,
      boolean dummy)
      throws IOException {
    cdh = header;
    this.file = file;
    deleted = false;
    verifyLog = file.makeVerifyLog();
    this.storage = storage;
    this.linkedEntry = linkedEntry;
    this.nestedOffset = nestedOffset;
    this.dummy = dummy;

    if (header.getOffset() >= 0) {
      readLocalHeader();

      Preconditions.checkArgument(
          source == null, "Source was defined but contents already exist on file.");

      /*
       * Since the file is already in the zip, dynamically create a source that will read
       * the file from the zip when needed. The assignment is not really needed, but we
       * would get a warning because of the @NotNull otherwise.
       */
      this.source = createSourceFromZip(cdh.getOffset());
    } else {
      /*
       * There is no local extra data for new files.
       */
      localExtra = new ExtraField();

      Preconditions.checkNotNull(source, "Source was not defined, but contents are not on file.");
      this.source = source;
    }

    /*
     * It seems that zip utilities store directories as names ending with "/".
     * This seems to be respected by all zip utilities although I could not find there anywhere
     * in the specification.
     */
    if (cdh.getName().endsWith(Character.toString(ZFile.SEPARATOR))) {
      type = StoredEntryType.DIRECTORY;
      verifyLog.verify(
          this.source.getProcessedByteSource().isEmpty(), "Directory source is not empty.");
      verifyLog.verify(cdh.getCrc32() == 0, "Directory has CRC32 = %s.", cdh.getCrc32());
      verifyLog.verify(
          cdh.getUncompressedSize() == 0,
          "Directory has uncompressed size = %s.",
          cdh.getUncompressedSize());

      /*
       * Some clever (OMG!) tools, like jar will actually try to compress the directory
       * contents and generate a 2 byte compressed data. Of course, the uncompressed size is
       * zero and we're just wasting space.
       */
      long compressedSize = cdh.getCompressionInfoWithWait().getCompressedSize();
      verifyLog.verify(
          compressedSize == 0 || compressedSize == 2,
          "Directory has compressed size = %s.",
          compressedSize);
    } else {
      type = StoredEntryType.FILE;
    }

    /*
     * By default we assume there is no data descriptor unless the CRC is marked as deferred
     * in the header's GP Bit.
     */
    dataDescriptorType = Suppliers.ofInstance(DataDescriptorType.NO_DATA_DESCRIPTOR);
    if (header.getGpBit().isDeferredCrc()) {
      /*
       * If the deferred CRC bit exists, then we have an extra descriptor field. This extra
       * field may have a signature.
       */
      Verify.verify(
          header.getOffset() >= 0,
          "Files that are not on disk cannot have the " + "deferred CRC bit set.");

      dataDescriptorType =
          Suppliers.memoize(
              () -> {
                try {
                  return readDataDescriptorRecord();
                } catch (IOException e) {
                  throw new IOExceptionWrapper(
                      new IOException("Failed to read data descriptor record.", e));
                }
              });
    }
  }

  /**
   * Obtains the size of the local header of this entry.
   *
   * @return the local header size in bytes
   */
  public int getLocalHeaderSize() {
    Preconditions.checkState(!deleted, "deleted");
    return FIXED_LOCAL_FILE_HEADER_SIZE + cdh.getEncodedFileName().length + localExtra.size();
  }

  /**
   * Obtains the size of the whole entry on disk, including local header and data descriptor. This
   * method will wait until compression information is complete, if needed.
   *
   * @return the number of bytes
   * @throws IOException failed to get compression information
   */
  long getInFileSize() throws IOException {
    Preconditions.checkState(!deleted, "deleted");
    return cdh.getCompressionInfoWithWait().getCompressedSize()
        + getLocalHeaderSize()
        + dataDescriptorType.get().size;
  }

  /**
   * Obtains a stream that allows reading from the entry.
   *
   * @return a stream that will return as many bytes as the uncompressed entry size
   * @throws IOException failed to open the stream
   */
  public InputStream open() throws IOException {
    return source.getProcessedByteSource().openStream();
  }

  /**
   * Obtains the contents of the file.
   *
   * @return a byte array with the contents of the file (uncompressed if the file was compressed)
   * @throws IOException failed to read the file
   */
  public byte[] read() throws IOException {
    try (InputStream is = new BufferedInputStream(open())) {
      return ByteStreams.toByteArray(is);
    }
  }

  /**
   * Obtains the contents of the file in an existing buffer.
   *
   * @param bytes buffer to read the file contents in.
   * @return the number of bytes read
   * @throws IOException failed to read the file.
   */
  public int read(byte[] bytes) throws IOException {
    if (bytes.length < getCentralDirectoryHeader().getUncompressedSize()) {
      throw new RuntimeException(
          "Buffer to small while reading {}" + getCentralDirectoryHeader().getName());
    }
    try (InputStream is = new BufferedInputStream(open())) {
      return ByteStreams.read(is, bytes, 0, bytes.length);
    }
  }

  /**
   * Obtains the type of entry.
   *
   * @return the type of entry
   */
  public StoredEntryType getType() {
    Preconditions.checkState(!deleted, "deleted");
    return type;
  }

  /**
   * Deletes this entry from the zip file. Invoking this method doesn't update the zip itself. To
   * eventually write updates to disk, {@link ZFile#update()} must be called.
   *
   * @throws IOException failed to delete the entry
   * @throws IllegalStateException if the zip file was open in read-only mode
   */
  public void delete() throws IOException {
    delete(true);
  }

  /**
   * Deletes this entry from the zip file. Invoking this method doesn't update the zip itself. To
   * eventually write updates to disk, {@link ZFile#update()} must be called.
   *
   * @param notify should listeners be notified of the deletion? This will only be {@code false} if
   *     the entry is being removed as part of a replacement
   * @throws IOException failed to delete the entry
   * @throws IllegalStateException if the zip file was open in read-only mode
   */
  void delete(boolean notify) throws IOException {
    Preconditions.checkState(!deleted, "deleted");
    file.delete(this, notify);
    deleted = true;
    source.close();
  }

  /** Returns {@code true} if this entry has been deleted/replaced. */
  public boolean isDeleted() {
    return deleted;
  }

  /**
   * Obtains the CDH associated with this entry.
   *
   * @return the CDH
   */
  public CentralDirectoryHeader getCentralDirectoryHeader() {
    return cdh;
  }

  /**
   * Reads the file's local header and verifies that it matches the Central Directory Header
   * provided in the constructor. This method should only be called if the entry already exists on
   * disk; new entries do not have local headers.
   *
   * <p>This method will define the {@link #localExtra} field that is only defined in the local
   * descriptor.
   *
   * @throws IOException failed to read the local header
   */
  private void readLocalHeader() throws IOException {
    byte[] localHeader = new byte[FIXED_LOCAL_FILE_HEADER_SIZE];
    file.directFullyRead(cdh.getOffset(), localHeader);

    CentralDirectoryHeaderCompressInfo compressInfo = cdh.getCompressionInfoWithWait();

    ByteBuffer bytes = ByteBuffer.wrap(localHeader);
    F_LOCAL_SIGNATURE.verify(bytes);
    F_VERSION_EXTRACT.verify(bytes, compressInfo.getVersionExtract(), verifyLog);
    F_GP_BIT.verify(bytes, cdh.getGpBit().getValue(), verifyLog);
    F_METHOD.verify(bytes, compressInfo.getMethod().methodCode, verifyLog);

    if (file.areTimestampsIgnored()) {
      F_LAST_MOD_TIME.skip(bytes);
      F_LAST_MOD_DATE.skip(bytes);
    } else {
      F_LAST_MOD_TIME.verify(bytes, cdh.getLastModTime(), verifyLog);
      F_LAST_MOD_DATE.verify(bytes, cdh.getLastModDate(), verifyLog);
    }

    /*
     * If CRC-32, compressed size and uncompressed size are deferred, their values in Local
     * File Header must be ignored and their actual values must be read from the Data
     * Descriptor following the contents of this entry. See readDataDescriptorRecord().
     */
    if (cdh.getGpBit().isDeferredCrc()) {
      F_CRC32.skip(bytes);
      F_COMPRESSED_SIZE.skip(bytes);
      F_UNCOMPRESSED_SIZE.skip(bytes);
    } else {
      F_CRC32.verify(bytes, cdh.getCrc32(), verifyLog);
      F_COMPRESSED_SIZE.verify(bytes, compressInfo.getCompressedSize(), verifyLog);
      F_UNCOMPRESSED_SIZE.verify(bytes, cdh.getUncompressedSize(), verifyLog);
    }

    F_FILE_NAME_LENGTH.verify(bytes, cdh.getEncodedFileName().length);
    long extraLength = F_EXTRA_LENGTH.read(bytes);
    long fileNameStart = cdh.getOffset() + F_EXTRA_LENGTH.endOffset();
    byte[] fileNameData = new byte[cdh.getEncodedFileName().length];
    file.directFullyRead(fileNameStart, fileNameData);

    String fileName = EncodeUtils.decode(fileNameData, cdh.getGpBit());
    if (!fileName.equals(cdh.getName())) {
      verifyLog.log(
          String.format(
              "Central directory reports file as being named '%s' but local header"
                  + "reports file being named '%s'.",
              cdh.getName(), fileName));
    }

    long localExtraStart = fileNameStart + cdh.getEncodedFileName().length;
    byte[] localExtraRaw = new byte[Ints.checkedCast(extraLength)];
    file.directFullyRead(localExtraStart, localExtraRaw);
    localExtra = new ExtraField(localExtraRaw);
  }

  /**
   * Reads the data descriptor record. This method can only be invoked once it is established that a
   * data descriptor does exist. It will read the data descriptor and check that the data described
   * there matches the data provided in the Central Directory.
   *
   * <p>This method will set the {@link #dataDescriptorType} field to the appropriate type of data
   * descriptor record.
   *
   * @throws IOException failed to read the data descriptor record
   */
  private DataDescriptorType readDataDescriptorRecord() throws IOException {
    CentralDirectoryHeaderCompressInfo compressInfo = cdh.getCompressionInfoWithWait();

    long ddStart =
        cdh.getOffset()
            + FIXED_LOCAL_FILE_HEADER_SIZE
            + cdh.getName().length()
            + localExtra.size()
            + compressInfo.getCompressedSize();
    byte[] ddData = new byte[DataDescriptorType.DATA_DESCRIPTOR_WITH_SIGNATURE.size];
    file.directFullyRead(ddStart, ddData);

    ByteBuffer ddBytes = ByteBuffer.wrap(ddData);

    ZipField.F4 signatureField = new ZipField.F4(0, "Data descriptor signature");
    int cpos = ddBytes.position();
    long sig = signatureField.read(ddBytes);
    DataDescriptorType result;
    if (sig == DATA_DESC_SIGNATURE) {
      result = DataDescriptorType.DATA_DESCRIPTOR_WITH_SIGNATURE;
    } else {
      result = DataDescriptorType.DATA_DESCRIPTOR_WITHOUT_SIGNATURE;
      ddBytes.position(cpos);
    }

    ZipField.F4 crc32Field = new ZipField.F4(0, "CRC32");
    ZipField.F4 compressedField = new ZipField.F4(crc32Field.endOffset(), "Compressed size");
    ZipField.F4 uncompressedField =
        new ZipField.F4(compressedField.endOffset(), "Uncompressed size");

    crc32Field.verify(ddBytes, cdh.getCrc32(), verifyLog);
    compressedField.verify(ddBytes, compressInfo.getCompressedSize(), verifyLog);
    uncompressedField.verify(ddBytes, cdh.getUncompressedSize(), verifyLog);
    return result;
  }

  /**
   * Creates a new source that reads data from the zip.
   *
   * @param zipOffset the offset into the zip file where the data is, must be non-negative
   * @throws IOException failed to close the old source
   * @return the created source
   */
  private ProcessedAndRawByteSources createSourceFromZip(final long zipOffset) throws IOException {
    Preconditions.checkArgument(zipOffset >= 0, "zipOffset < 0");

    final CentralDirectoryHeaderCompressInfo compressInfo;
    try {
      compressInfo = cdh.getCompressionInfoWithWait();
    } catch (IOException e) {
      throw new RuntimeException(
          "IOException should never occur here because compression "
              + "information should be immediately available if reading from zip.",
          e);
    }

    /*
     * Create a source that will return whatever is on the zip file.
     */
    CloseableByteSource rawContents =
        new CloseableByteSource() {
          @Override
          public long size() throws IOException {
            return compressInfo.getCompressedSize();
          }

          @Override
          public InputStream openStream() throws IOException {
            Preconditions.checkState(!deleted, "deleted");

            long dataStart = zipOffset + getLocalHeaderSize();
            long dataEnd = dataStart + compressInfo.getCompressedSize();

            file.openReadOnlyIfClosed();
            return file.directOpen(dataStart, dataEnd);
          }

          @Override
          protected void innerClose() throws IOException {
            /*
             * Nothing to do here.
             */
          }
        };

    return createSourcesFromRawContents(rawContents);
  }

  /**
   * Creates a {@link ProcessedAndRawByteSources} from the raw data source . The processed source
   * will either inflate or do nothing depending on the compression information that, at this point,
   * should already be available
   *
   * @param rawContents the raw data to create the source from
   * @return the sources for this entry
   */
  private ProcessedAndRawByteSources createSourcesFromRawContents(CloseableByteSource rawContents) {
    CentralDirectoryHeaderCompressInfo compressInfo;
    try {
      compressInfo = cdh.getCompressionInfoWithWait();
    } catch (IOException e) {
      throw new RuntimeException(
          "IOException should never occur here because compression "
              + "information should be immediately available if creating from raw "
              + "contents.",
          e);
    }

    CloseableByteSource contents;

    /*
     * If the contents are deflated, wrap that source in an inflater source so we get the
     * uncompressed data.
     */
    if (compressInfo.getMethod() == CompressionMethod.DEFLATE) {
      contents = new InflaterByteSource(rawContents);
    } else {
      contents = rawContents;
    }

    return new ProcessedAndRawByteSources(contents, rawContents);
  }

  /**
   * Replaces {@link #source} with one that reads file data from the zip file.
   *
   * @param zipFileOffset the offset in the zip file where data is written; must be non-negative
   * @throws IOException failed to replace the source
   */
  void replaceSourceFromZip(long zipFileOffset) throws IOException {
    Preconditions.checkArgument(zipFileOffset >= 0, "zipFileOffset < 0");

    ProcessedAndRawByteSources oldSource = source;
    source = createSourceFromZip(zipFileOffset);
    cdh.setOffset(zipFileOffset);
    if (!isLinkingEntry())
      oldSource.close();
  }

  /**
   * Loads all data in memory and replaces {@link #source} with one that contains all the data in
   * memory.
   *
   * <p>If the entry's contents are already in memory, this call does nothing.
   *
   * @throws IOException failed to replace the source
   */
  void loadSourceIntoMemory() throws IOException {
    if (cdh.getOffset() == -1) {
      /*
       * No offset in the CDR means data has not been written to disk which, in turn,
       * means data is already loaded into memory.
       */
      return;
    }

    CloseableByteSourceFromOutputStreamBuilder rawBuilder = storage.makeBuilder();
    try (InputStream input = source.getRawByteSource().openStream()) {
      ByteStreams.copy(input, rawBuilder);
    }

    CloseableByteSource newRaw = rawBuilder.build();
    ProcessedAndRawByteSources newSources = createSourcesFromRawContents(newRaw);

    try (ProcessedAndRawByteSources oldSource = source) {
      source = newSources;
      cdh.setOffset(-1);
    }
  }

  /**
   * Obtains the source data for this entry. This method can only be called for files, it cannot be
   * called for directories.
   *
   * @return the entry source
   */
  ProcessedAndRawByteSources getSource() {
    return source;
  }

  /**
   * Obtains the type of data descriptor used in the entry.
   *
   * @return the type of data descriptor
   */
  public DataDescriptorType getDataDescriptorType() {
    return dataDescriptorType.get();
  }

  /**
   * Removes the data descriptor, if it has one and resets the data descriptor bit in the central
   * directory header.
   *
   * @return was the data descriptor remove?
   */
  boolean removeDataDescriptor() {
    if (dataDescriptorType.get() == DataDescriptorType.NO_DATA_DESCRIPTOR) {
      return false;
    }

    dataDescriptorType = Suppliers.ofInstance(DataDescriptorType.NO_DATA_DESCRIPTOR);
    cdh.resetDeferredCrc();
    return true;
  }

  /**
   * Obtains the local header data.
   *
   * @param buffer a buffer to write header data to
   * @return the header data size
   * @throws IOException failed to get header byte data
   */
  int toHeaderData(byte[] buffer) throws IOException {
    Preconditions.checkArgument(
        buffer.length
            >= F_EXTRA_LENGTH.endOffset() + cdh.getEncodedFileName().length + localExtra.size(),
        "Buffer should be at least the header size");

    ByteBuffer out = ByteBuffer.wrap(buffer);
    writeData(out);
    return out.position();
  }

  private void writeData(ByteBuffer out) throws IOException {
    writeData(out, 0);
  }

  void writeData(ByteBuffer out, int extraOffset) throws IOException {
    Preconditions.checkArgument(extraOffset >= 0 , "extraOffset < 0");
    CentralDirectoryHeaderCompressInfo compressInfo = cdh.getCompressionInfoWithWait();

    F_LOCAL_SIGNATURE.write(out);
    F_VERSION_EXTRACT.write(out, compressInfo.getVersionExtract());
    F_GP_BIT.write(out, cdh.getGpBit().getValue());
    F_METHOD.write(out, compressInfo.getMethod().methodCode);

    if (file.areTimestampsIgnored()) {
      F_LAST_MOD_TIME.write(out, 0);
      F_LAST_MOD_DATE.write(out, 0);
    } else {
      F_LAST_MOD_TIME.write(out, cdh.getLastModTime());
      F_LAST_MOD_DATE.write(out, cdh.getLastModDate());
    }

    F_CRC32.write(out, cdh.getCrc32());
    F_COMPRESSED_SIZE.write(out, compressInfo.getCompressedSize());
    F_UNCOMPRESSED_SIZE.write(out, cdh.getUncompressedSize());
    F_FILE_NAME_LENGTH.write(out, cdh.getEncodedFileName().length);
    F_EXTRA_LENGTH.write(out, localExtra.size() + extraOffset + nestedOffset);

    out.put(cdh.getEncodedFileName());
    localExtra.write(out);
  }

  /**
   * Requests that this entry be realigned. If this entry is already aligned according to the rules
   * in {@link ZFile} then this method does nothing. Otherwise it will move the file's data into
   * memory and place it in a different area of the zip.
   *
   * @return has this file been changed? Note that if the entry has not yet been written on the
   *     file, realignment does not count as a change as nothing needs to be updated in the file;
   *     also, if the entry has been changed, this object may have been marked as deleted and a new
   *     stored entry may need to be fetched from the file
   * @throws IOException failed to realign the entry; the entry may no longer exist in the zip file
   */
  public boolean realign() throws IOException {
    Preconditions.checkState(!deleted, "Entry has been deleted.");

    if (isLinkingEntry()) return true;

    return file.realign(this);
  }

  public boolean isLinkingEntry() {
    return linkedEntry != null;
  }

  public boolean isDummyEntry() {
    return dummy;
  }

  public long getNestedOffset() {
    return nestedOffset;
  }

  /**
   * Obtains the contents of the local extra field.
   *
   * @return the contents of the local extra field
   */
  public ExtraField getLocalExtra() {
    return localExtra;
  }

  /**
   * Sets the contents of the local extra field.
   *
   * @param localExtra the contents of the local extra field
   * @throws IOException failed to update the zip file
   */
  public void setLocalExtra(ExtraField localExtra) throws IOException {
    boolean resized = setLocalExtraNoNotify(localExtra);
    file.localHeaderChanged(this, resized);
  }

  /**
   * Sets the contents of the local extra field, does not notify the {@link ZFile} of the change.
   * This is used internally when the {@link ZFile} itself wants to change the local extra and
   * doesn't need the callback.
   *
   * @param localExtra the contents of the local extra field
   * @return has the local header size changed?
   * @throws IOException failed to load the file
   */
  boolean setLocalExtraNoNotify(ExtraField localExtra) throws IOException {
    boolean sizeChanged;

    /*
     * Make sure we load into memory.
     *
     * If we change the size of the local header, the actual start of the file changes
     * according to our in-memory structures so, if we don't read the file now, we won't be
     * able to load it later :)
     *
     * But, even if the size doesn't change, we need to read it force the entry to be
     * rewritten otherwise the changes in the local header aren't written. Of course this case
     * may be optimized with some extra complexity added :)
     */
    loadSourceIntoMemory();

    if (this.localExtra.size() != localExtra.size()) {
      sizeChanged = true;
    } else {
      sizeChanged = false;
    }

    this.localExtra = localExtra;
    return sizeChanged;
  }

  /**
   * Obtains the verify log for the entry.
   *
   * @return the verify log
   */
  public VerifyLog getVerifyLog() {
    return verifyLog;
  }
}
