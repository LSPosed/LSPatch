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

import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.tools.build.apkzlib.bytestorage.ByteStorage;
import com.android.tools.build.apkzlib.utils.CachedFileContents;
import com.android.tools.build.apkzlib.utils.IOExceptionFunction;
import com.android.tools.build.apkzlib.utils.IOExceptionRunnable;
import com.android.tools.build.apkzlib.zip.compress.Zip64NotSupportedException;
import com.android.tools.build.apkzlib.zip.utils.ByteTracker;
import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import com.android.tools.build.apkzlib.zip.utils.CloseableDelegateByteSource;
import com.android.tools.build.apkzlib.zip.utils.LittleEndianUtils;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Verify;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.Closer;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/**
 * The {@code ZFile} provides the main interface for interacting with zip files. A {@code ZFile} can
 * be created on a new file or in an existing file. Once created, files can be added or removed from
 * the zip file.
 *
 * <p>Changes in the zip file are always deferred. Any change requested is made in memory and
 * written to disk only when {@link #update()} or {@link #close()} is invoked.
 *
 * <p>Zip files are open initially in read-only mode and will switch to read-write when needed. This
 * is done automatically. Because modifications to the file are done in-memory, the zip file can be
 * manipulated when closed. When invoking {@link #update()} or {@link #close()} the zip file will be
 * reopen and changes will be written. However, the zip file cannot be modified outside the control
 * of {@code ZFile}. So, if a {@code ZFile} is closed, modified outside and then a file is added or
 * removed from the zip file, when reopening the zip file, {@link ZFile} will detect the outside
 * modification and will fail.
 *
 * <p>In memory manipulation means that files added to the zip file are kept in memory until written
 * to disk. This provides much faster operation and allows better zip file allocation (see below).
 * It may, however, increase the memory footprint of the application. When adding large files, if
 * memory consumption is a concern, a call to {@link #update()} will actually write the file to disk
 * and discard the memory buffer. Information about allocation can be obtained from a {@link
 * ByteTracker} that can be given to the file on creation.
 *
 * <p>{@code ZFile} keeps track of allocation inside of the zip file. If a file is deleted, its
 * space is marked as freed and will be reused for an added file if it fits in the space. Allocation
 * of files to empty areas is done using a <em>best fit</em> algorithm. When adding a file, if it
 * doesn't fit in any free area, the zip file will be extended.
 *
 * <p>{@code ZFile} provides a fast way to merge data from another zip file (see {@link
 * #mergeFrom(ZFile, Predicate)}) avoiding recompression and copying of equal files. When merging,
 * patterns of files may be provided that are ignored. This allows handling special files in the
 * merging process, such as files in {@code META-INF}.
 *
 * <p>When adding files to the zip file, unless files are explicitly required to be stored, files
 * will be deflated. However, deflating will not occur if the deflated file is larger then the
 * stored file, <em>e.g.</em> if compression would yield a bigger file. See {@link Compressor} for
 * details on how compression works.
 *
 * <p>Because {@code ZFile} was designed to be used in a build system and not as general-purpose zip
 * utility, it is very strict (and unforgiving) about the zip format and unsupported features.
 *
 * <p>{@code ZFile} supports <em>alignment</em>. Alignment means that file data (not entries -- the
 * local header must be discounted) must start at offsets that are multiple of a number -- the
 * alignment. Alignment is defined by an alignment rules ({@link AlignmentRule} in the {@link
 * ZFileOptions} object used to create the {@link ZFile}.
 *
 * <p>When a file is added to the zip, the alignment rules will be checked and alignment will be
 * honored when positioning the file in the zip. This means that unused spaces in the zip may be
 * generated as a result. However, alignment of existing entries will not be changed.
 *
 * <p>Entries can be realigned individually (see {@link StoredEntry#realign()} or the full zip file
 * may be realigned (see {@link #realign()}). When realigning the full zip entries that are already
 * aligned will not be affected.
 *
 * <p>Because realignment may cause files to move in the zip, realignment is done in-memory meaning
 * that files that need to change location will moved to memory and will only be flushed when either
 * {@link #update()} or {@link #close()} are called.
 *
 * <p>Alignment only applies to filed that are forced to be uncompressed. This is because alignment
 * is used to allow mapping files in the archive directly into memory and compressing defeats the
 * purpose of alignment.
 *
 * <p>Manipulating zip files with {@link ZFile} may yield zip files with empty spaces between files.
 * This happens in two situations: (1) if alignment is required, files may be shifted to conform to
 * the request alignment leaving an empty space before the previous file, and (2) if a file is
 * removed or replaced with a file that does not fit the space it was in. By default, {@link ZFile}
 * does not do any special processing in these situations. Files are indexed by their offsets from
 * the central directory and empty spaces can exist in the zip file.
 *
 * <p>However, it is possible to tell {@link ZFile} to use the extra field in the local header to do
 * cover the empty spaces. This is done by setting {@link
 * ZFileOptions#setCoverEmptySpaceUsingExtraField(boolean)} to {@code true}. This has the advantage
 * of leaving no gaps between entries in the zip, as required by some tools like Oracle's {code jar}
 * tool. However, setting this option will destroy the contents of the file's extra field.
 *
 * <p>Activating {@link ZFileOptions#setCoverEmptySpaceUsingExtraField(boolean)} may lead to
 * <i>virtual files</i> being added to the zip file. Since extra field is limited to 64k, it is not
 * possible to cover any space bigger than that using the extra field. In those cases, <i>virtual
 * files</i> are added to the file. A virtual file is a file that exists in the actual zip data, but
 * is not referenced from the central directory. A zip-compliant utility should ignore these files.
 * However, zip utilities that expect the zip to be a stream, such as Oracle's jar, will find these
 * files instead of considering the zip to be corrupt.
 *
 * <p>{@code ZFile} support sorting zip files. Sorting (done through the {@link #sortZipContents()}
 * method) is a process by which all files are re-read into memory, if not already in memory,
 * removed from the zip and re-added in alphabetical order, respecting alignment rules. So, in
 * general, file {@code b} will come after file {@code a} unless file {@code a} is subject to
 * alignment that forces an empty space before that can be occupied by {@code b}. Sorting can be
 * used to minimize the changes between two zips.
 *
 * <p>Sorting in {@code ZFile} can be done manually or automatically. Manual sorting is done by
 * invoking {@link #sortZipContents()}. Automatic sorting is done by setting the {@link
 * ZFileOptions#getAutoSortFiles()} option when creating the {@code ZFile}. Automatic sorting
 * invokes {@link #sortZipContents()} immediately when doing an {@link #update()} after all
 * extensions have processed the {@link ZFileExtension#beforeUpdate()}. This has the guarantee that
 * files added by extensions will be sorted, something that does not happen if the invocation is
 * sequential, <i>i.e.</i>, {@link #sortZipContents()} called before {@link #update()}. The drawback
 * of automatic sorting is that sorting will happen every time {@link #update()} is called and the
 * file is dirty having a possible penalty in performance.
 *
 * <p>To allow whole-apk signing, the {@code ZFile} allows the central directory location to be
 * offset by a fixed amount. This amount can be set using the {@link #setExtraDirectoryOffset(long)}
 * method. Setting a non-zero value will add extra (unused) space in the zip file before the central
 * directory. This value can be changed at any time and it will force the central directory
 * rewritten when the file is updated or closed.
 *
 * <p>{@code ZFile} provides an extension mechanism to allow objects to register with the file and
 * be notified when changes to the file happen. This should be used to add extra features to the zip
 * file while providing strong decoupling. See {@link ZFileExtension}, {@link
 * ZFile#addZFileExtension(ZFileExtension)} and {@link ZFile#removeZFileExtension(ZFileExtension)}.
 *
 * <p>This class is <strong>not</strong> thread-safe. Neither are any of the classes associated with
 * it in this package, except when otherwise noticed.
 */
public class ZFile implements Closeable {

  /**
   * The file separator in paths in the zip file. This is fixed by the zip specification (section
   * 4.4.17).
   */
  public static final char SEPARATOR = '/';

  /** Minimum size the EOCD can have. */
  private static final int MIN_EOCD_SIZE = 22;

  /** Number of bytes of the Zip64 EOCD locator record. */
  private static final int ZIP64_EOCD_LOCATOR_SIZE = 20;

  /** Maximum size for the EOCD. */
  private static final int MAX_EOCD_COMMENT_SIZE = 65535;

  /** How many bytes to look back from the end of the file to look for the EOCD signature. */
  private static final int LAST_BYTES_TO_READ = MIN_EOCD_SIZE + MAX_EOCD_COMMENT_SIZE;

  /** Signature of the Zip64 EOCD locator record. */
  private static final int ZIP64_EOCD_LOCATOR_SIGNATURE = 0x07064b50;

  /** Signature of the EOCD record. */
  private static final byte[] EOCD_SIGNATURE = new byte[] {0x06, 0x05, 0x4b, 0x50};

  /** Size of buffer for I/O operations. */
  private static final int IO_BUFFER_SIZE = 1024 * 1024;

  /**
   * When extensions request re-runs, we do maximum number of cycles until we decide to stop and
   * flag a infinite recursion problem.
   */
  private static final int MAXIMUM_EXTENSION_CYCLE_COUNT = 10;

  /**
   * Minimum size for the extra field when we have to add one. We rely on the alignment segment to
   * do that so the minimum size for the extra field is the minimum size of an alignment segment.
   */
  protected static final int MINIMUM_EXTRA_FIELD_SIZE = ExtraField.AlignmentSegment.MINIMUM_SIZE;

  /**
   * Maximum size of the extra field.
   *
   * <p>Theoretically, this is (1 << 16) - 1 = 65535 and not (1 < 15) -1 = 32767. However, due to
   * http://b.android.com/221703, we need to keep this limited.
   */
  protected static final int MAX_LOCAL_EXTRA_FIELD_CONTENTS_SIZE = (1 << 15) - 1;

  /** File zip file. */
  protected final File file;

  /**
   * The random access file used to access the zip file. This will be {@code null} if and only if
   * {@link #state} is {@link ZipFileState#CLOSED}.
   */
  @Nullable private RandomAccessFile raf;

  /**
   * The map containing the in-memory contents of the zip file. It keeps track of which parts of the
   * zip file are used and which are not.
   */
  private final FileUseMap map;

  /**
   * The EOCD entry. Will be {@code null} if there is no EOCD (because the zip is new) or the one
   * that exists on disk is no longer valid (because the zip has been changed).
   *
   * <p>If the EOCD is deleted because the zip has been changed and the old EOCD was no longer
   * valid, then {@link #eocdComment} will contain the comment saved from the EOCD.
   */
  @Nullable private FileUseMapEntry<Eocd> eocdEntry;

  /**
   * The Central Directory entry. Will be {@code null} if there is no Central Directory (because the
   * zip is new) or because the one that exists on disk is no longer valid (because the zip has been
   * changed).
   */
  @Nullable private FileUseMapEntry<CentralDirectory> directoryEntry;

  /**
   * All entries in the zip file. It includes in-memory changes and may not reflect what is written
   * on disk. Only entries that have been compressed are in this list.
   */
  private final Map<String, FileUseMapEntry<StoredEntry>> entries;

  /**
   * Entries added to the zip file, but that are not yet compressed. When compression is done, these
   * entries are eventually moved to {@link #entries}. uncompressedEntries is a list because entries
   * need to be kept in the order by which they were added. It allows adding multiple files with the
   * same name and getting the right notifications on which files replaced which.
   *
   * <p>Files are placed in this list in {@link #add(StoredEntry)} method. This method will keep
   * files here temporarily and move then to {@link #entries} when the data is available.
   *
   * <p>Moving files out of this list to {@link #entries} is done by {@link
   * #processAllReadyEntries()}.
   */
  private final List<StoredEntry> uncompressedEntries;

  /** LSPatch
   *
   */
  private final List<StoredEntry> linkingEntries;

  /** Current state of the zip file. */
  private ZipFileState state;

  /**
   * Are the in-memory changes that have not been written to the zip file?
   *
   * <p>This might be false, but will become true after {@link #processAllReadyEntriesWithWait()} is
   * called if there are {@link #uncompressedEntries} compressing in the background.
   */
  private boolean dirty;

  /**
   * Non-{@code null} only if the file is currently closed. Used to detect if the zip is modified
   * outside this object's control. If the file has never been written, this will be {@code null}
   * even if it is closed.
   */
  @Nullable private CachedFileContents<Object> closedControl;

  /** The alignment rule. */
  private final AlignmentRule alignmentRule;

  /** Extensions registered with the file. */
  private final List<ZFileExtension> extensions;

  /**
   * When notifying extensions, extensions may request that some runnables are executed. This list
   * collects all runnables by the order they were requested. Together with {@link #isNotifying}, it
   * is used to avoid reordering notifications.
   */
  private final List<IOExceptionRunnable> toRun;

  /**
   * {@code true} when {@link #notify(com.android.tools.build.apkzlib.utils.IOExceptionFunction)} is
   * notifying extensions. Used to avoid reordering notifications.
   */
  private boolean isNotifying;

  /**
   * An extra offset for the central directory location. {@code 0} if the central directory should
   * be written in its standard location.
   */
  private long extraDirectoryOffset;

  /** Should all timestamps be zeroed when reading / writing the zip? */
  private boolean noTimestamps;

  /** Compressor to use. */
  private final Compressor compressor;

  /** Byte storage to use. */
  private final ByteStorage storage;

  /** Use the zip entry's "extra field" field to cover empty space in the zip file? */
  private boolean coverEmptySpaceUsingExtraField;

  /** Should files be automatically sorted when updating? */
  private boolean autoSortFiles;

  /** Verify log factory to use. */
  private final Supplier<VerifyLog> verifyLogFactory;

  /** Verify log to use. */
  private final VerifyLog verifyLog;

  /** Should skip expensive validation? */
  private final boolean skipValidation;

  /**
   * This field contains the comment in the zip's EOCD if there is no in-memory EOCD structure. This
   * may happen, for example, if the zip has been changed and the Central Directory and EOCD have
   * been deleted (in-memory). In that case, this field will save the comment to place on the EOCD
   * once it is created.
   *
   * <p>This field will only be non-{@code null} if there is no in-memory EOCD structure
   * (<i>i.e.</i>, {@link #eocdEntry} is {@code null}). If there is an {@link #eocdEntry}, then the
   * comment will be there instead of being in this field.
   */
  @Nullable private byte[] eocdComment;

  /** Is the file in read-only mode? In read-only mode no changes are allowed. */
  private boolean readOnly;

  /**
   * Creates a new zip file. If the zip file does not exist, then no file is created at this point
   * and {@code ZFile} will contain an empty structure. However, an (empty) zip file will be created
   * if either {@link #update()} or {@link #close()} are used. If a zip file exists, it will be
   * parsed and read.
   *
   * @param file the zip file
   * @throws IOException some file exists but could not be read
   * @deprecated use {@link ZFile#openReadOnly(File)} or {@link ZFile#openReadWrite(File)}
   */
  @Deprecated
  public ZFile(File file) throws IOException {
    this(file, new ZFileOptions());
  }

  /**
   * Creates a new zip file. If the zip file does not exist, then no file is created at this point
   * and {@code ZFile} will contain an empty structure. However, an (empty) zip file will be created
   * if either {@link #update()} or {@link #close()} are used. If a zip file exists, it will be
   * parsed and read.
   *
   * @param file the zip file
   * @param options configuration options
   * @throws IOException some file exists but could not be read
   * @deprecated use {@link ZFile#openReadOnly(File, ZFileOptions)} or {@link
   *     ZFile#openReadWrite(File, ZFileOptions)}
   */
  @Deprecated
  public ZFile(File file, ZFileOptions options) throws IOException {
    this(file, options, false);
  }

  /**
   * Creates a new zip file. If the zip file does not exist, then no file is created at this point
   * and {@code ZFile} will contain an empty structure. However, an (empty) zip file will be created
   * if either {@link #update()} or {@link #close()} are used. If a zip file exists, it will be
   * parsed and read.
   *
   * @param file the zip file
   * @param options configuration options
   * @param readOnly should the file be open in read-only mode? If {@code true} then the file must
   *     exist and no methods can be invoked that could potentially change the file
   * @throws IOException some file exists but could not be read
   * @deprecated use {@link ZFile#openReadOnly(File, ZFileOptions)} or {@link
   *     ZFile#openReadWrite(File, ZFileOptions)}
   */
  @Deprecated
  public ZFile(File file, ZFileOptions options, boolean readOnly) throws IOException {
    this.file = file;
    map =
        new FileUseMap(
            0, options.getCoverEmptySpaceUsingExtraField() ? MINIMUM_EXTRA_FIELD_SIZE : 0);
    this.readOnly = readOnly;
    dirty = false;
    closedControl = null;
    alignmentRule = options.getAlignmentRule();
    extensions = Lists.newArrayList();
    toRun = Lists.newArrayList();
    noTimestamps = options.getNoTimestamps();
    storage = options.getStorageFactory().create();
    compressor = options.getCompressor();
    coverEmptySpaceUsingExtraField = options.getCoverEmptySpaceUsingExtraField();
    autoSortFiles = options.getAutoSortFiles();
    verifyLogFactory = options.getVerifyLogFactory();
    verifyLog = verifyLogFactory.get();
    skipValidation = options.getSkipValidation();

    /*
     * These two values will be overwritten by openReadOnlyIfClosed() below if the file exists.
     */
    state = ZipFileState.CLOSED;
    raf = null;

    if (file.exists()) {
      openReadOnlyIfClosed();
    } else if (readOnly) {
      throw new IOException("File does not exist but read-only mode requested");
    } else {
      dirty = true;
    }

    entries = Maps.newHashMap();
    uncompressedEntries = Lists.newArrayList();
    linkingEntries = Lists.newArrayList();
    extraDirectoryOffset = 0;

    try {
      if (state != ZipFileState.CLOSED) {
        // TODO: to be removed completely once Zip64 is fully supported
        final long MAX_ENTRY_SIZE = 0xFFFFFFFFL; // 2^32-1
        long rafSize = raf.length();
        if (rafSize > MAX_ENTRY_SIZE) {
          throw new IOException("File exceeds size limit of " + MAX_ENTRY_SIZE + ".");
        }

        map.extend(raf.length());
        readData();
      }

      // If we don't have an EOCD entry, set the comment to empty.
      if (eocdEntry == null) {
        eocdComment = new byte[0];
      }

      // Notify the extensions if the zip file has been open.
      if (state != ZipFileState.CLOSED) {
        notify(ZFileExtension::open);
      }
    } catch (Zip64NotSupportedException e) {
      throw e;
    } catch (IOException e) {
      throw new IOException("Failed to read zip file '" + file.getAbsolutePath() + "'.", e);
    } catch (IllegalStateException | IllegalArgumentException | VerifyException e) {
      throw new RuntimeException(
          "Internal error when trying to read zip file '" + file.getAbsolutePath() + "'.", e);
    }
  }

  /**
   * Old name of {@link #openReadOnlyIfClosed()}, method kept for backwards compatibility only.
   *
   * @deprecated use {@link #openReadOnlyIfClosed()} if necessary to ensure a {@link ZFile} is open
   *     and readable
   */
  @Deprecated
  public void openReadOnly() throws IOException {
    openReadOnlyIfClosed();
  }

  /**
   * Opens a new {@link ZFile} from the given file in read-only mode.
   *
   * @param file the file to open
   * @return the created file
   * @throws IOException failed to read the file
   */
  public static ZFile openReadOnly(File file) throws IOException {
    return openReadOnly(file, new ZFileOptions());
  }

  /**
   * Opens a new {@link ZFile} from the given file in read-only mode.
   *
   * @param file the file to open
   * @param options the options to use to open the file; because the file is open read-only, many of
   *     these options won't have any effect
   * @return the created file
   * @throws IOException failed to read the file
   */
  public static ZFile openReadOnly(File file, ZFileOptions options) throws IOException {
    return new ZFile(file, options, true);
  }

  /**
   * Opens a new {@link ZFile} from the given file in read-write mode. Opening a file in read-write
   * mode may force the file to be written even if no changes are made. For example, differences in
   * signature will force the file to be written. Use {@link #openReadOnly(File, ZFileOptions)} to
   * open a file and ensure it won't be written.
   *
   * <p>The file will be created if it doesn't exist. If the file exists, it must be a valid zip
   * archive.
   *
   * @param file the file to open
   * @return the created file
   * @throws IOException failed to read the file
   */
  public static ZFile openReadWrite(File file) throws IOException {
    return openReadWrite(file, new ZFileOptions());
  }

  /**
   * Opens a new {@link ZFile} from the given file in read-write mode. Opening a file in read-write
   * mode may force the file to be written even if no changes are made. For example, differences in
   * signature will force the file to be written. Use {@link #openReadOnly(File, ZFileOptions)} to
   * open a file and ensure it won't be written.
   *
   * <p>The file will be created if it doesn't exist. If the file exists, it must be a valid zip
   * archive.
   *
   * @param file the file to open
   * @param options the options to use to open the file
   * @return the created file
   * @throws IOException failed to read the file
   */
  public static ZFile openReadWrite(File file, ZFileOptions options) throws IOException {
    return new ZFile(file, options, false);
  }

  public boolean getSkipValidation() {
    return skipValidation;
  }

  /**
   * Obtains all entries in the file. Entries themselves may be or not written in disk. However, all
   * of them can be open for reading.
   *
   * @return all entries in the zip
   */
  public Set<StoredEntry> entries() {
    Map<String, StoredEntry> entries = Maps.newHashMap();

    for (FileUseMapEntry<StoredEntry> mapEntry : this.entries.values()) {
      StoredEntry entry = mapEntry.getStore();
      Preconditions.checkNotNull(entry, "Entry at %s is null", mapEntry.getStart());
      entries.put(entry.getCentralDirectoryHeader().getName(), entry);
    }

    /*
     * mUncompressed may override mEntriesReady as we may not have yet processed all
     * entries.
     */
    for (StoredEntry uncompressed : uncompressedEntries) {
      entries.put(uncompressed.getCentralDirectoryHeader().getName(), uncompressed);
    }

    for (StoredEntry linking: linkingEntries) {
      entries.put(linking.getCentralDirectoryHeader().getName(), linking);
    }

    return Sets.newHashSet(entries.values());
  }

  /**
   * Obtains an entry at a given path in the zip.
   *
   * @param path the path
   * @return the entry at the path or {@code null} if none exists
   */
  @Nullable
  public StoredEntry get(String path) {
    /*
     * The latest entries are the last ones in uncompressed and they may eventually override
     * files in entries.
     */
    for (StoredEntry stillUncompressed : Lists.reverse(uncompressedEntries)) {
      if (stillUncompressed.getCentralDirectoryHeader().getName().equals(path)) {
        return stillUncompressed;
      }
    }

    FileUseMapEntry<StoredEntry> found = entries.get(path);
    if (found == null) {
      return null;
    }

    return found.getStore();
  }

  /**
   * Reads all the data in the zip file, except the contents of the entries themselves. This method
   * will populate the directory and maps in the instance variables.
   *
   * @throws IOException failed to read the zip file
   */
  private void readData() throws IOException {
    Preconditions.checkState(state != ZipFileState.CLOSED, "state == ZipFileState.CLOSED");
    Preconditions.checkNotNull(raf, "raf == null");

    readEocd();
    readCentralDirectory();

    /*
     * Go over all files and create the usage map, verifying there is no overlap in the files.
     */
    long entryEndOffset;
    long directoryStartOffset;

    if (directoryEntry != null) {
      CentralDirectory directory = directoryEntry.getStore();
      Preconditions.checkNotNull(directory, "Central directory is null");

      entryEndOffset = 0;

      for (StoredEntry entry : directory.getEntries().values()) {
        long start = entry.getCentralDirectoryHeader().getOffset();
        long end = start + entry.getInFileSize();

        /*
         * If isExtraAlignmentBlock(entry.getLocalExtra()) is true, we know the entry
         * has an extra field that is solely used for alignment. This means the
         * actual entry could start at start + extra.length and leave space before.
         *
         * But, if we did this here, we would be modifying the zip file and that is
         * weird because we're just opening it for reading.
         *
         * The downside is that we will never reuse that space. Maybe one day ZFile
         * can be clever enough to remove the local extra when we start modifying the zip
         * file.
         */

        Verify.verify(start >= 0, "start < 0");
        Verify.verify(end < map.size(), "end >= map.size()");

        FileUseMapEntry<?> found = map.at(start);
        Verify.verifyNotNull(found);

        // We've got a problem if the found entry is not free or is a free entry but
        // doesn't cover the whole file.
        if (!found.isFree() || found.getEnd() < end) {
          if (found.isFree()) {
            found = map.after(found);
            Verify.verify(found != null && !found.isFree());
          }

          Object foundEntry = found.getStore();
          Verify.verify(foundEntry != null);

          // Obtains a custom description of an entry.
          IOExceptionFunction<StoredEntry, String> describe =
              e ->
                  String.format(
                      "'%s' (offset: %d, size: %d)",
                      e.getCentralDirectoryHeader().getName(),
                      e.getCentralDirectoryHeader().getOffset(),
                      e.getInFileSize());

          String overlappingEntryDescription;
          if (foundEntry instanceof StoredEntry) {
            StoredEntry foundStored = (StoredEntry) foundEntry;
            overlappingEntryDescription = describe.apply(foundStored);
          } else {
            overlappingEntryDescription =
                "Central Directory / EOCD: " + found.getStart() + " - " + found.getEnd();
          }

          throw new IOException(
              "Cannot read entry "
                  + describe.apply(entry)
                  + " because it overlaps with "
                  + overlappingEntryDescription);
        }

        FileUseMapEntry<StoredEntry> mapEntry = map.add(start, end, entry);
        entries.put(entry.getCentralDirectoryHeader().getName(), mapEntry);

        if (end > entryEndOffset) {
          entryEndOffset = end;
        }
      }

      directoryStartOffset = directoryEntry.getStart();
    } else {
      /*
       * No directory means an empty zip file. Use the start of the EOCD to compute
       * an existing offset.
       */
      Verify.verifyNotNull(eocdEntry);
      Preconditions.checkNotNull(eocdEntry, "EOCD is null");
      directoryStartOffset = eocdEntry.getStart();
      entryEndOffset = 0;
    }

    /*
     * Check if there is an extra central directory offset. If there is, save it. Note that
     * we can't call extraDirectoryOffset() because that would mark the file as dirty.
     */
    long extraOffset = directoryStartOffset - entryEndOffset;
    Verify.verify(extraOffset >= 0, "extraOffset (%s) < 0", extraOffset);
    extraDirectoryOffset = extraOffset;
  }

  /**
   * Finds the EOCD marker and reads it. It will populate the {@link #eocdEntry} variable.
   *
   * @throws IOException failed to read the EOCD
   */
  private void readEocd() throws IOException {
    Preconditions.checkState(state != ZipFileState.CLOSED, "state == ZipFileState.CLOSED");
    Preconditions.checkNotNull(raf, "raf == null");

    /*
     * Read the last part of the zip into memory. If we don't find the EOCD signature by then,
     * the file is corrupt.
     */
    int lastToRead = LAST_BYTES_TO_READ;
    if (lastToRead > raf.length()) {
      lastToRead = Ints.checkedCast(raf.length());
    }

    byte[] last = new byte[lastToRead];
    directFullyRead(raf.length() - lastToRead, last);

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
    int foundEocdSignature = -1;
    IOException errorFindingSignature = null;
    long eocdStart = -1;

    for (int endIdx = last.length - MIN_EOCD_SIZE;
        endIdx >= 0 && foundEocdSignature == -1;
        endIdx--) {
      /*
       * Remember: little endian...
       */
      if (last[endIdx] == EOCD_SIGNATURE[3]
          && last[endIdx + 1] == EOCD_SIGNATURE[2]
          && last[endIdx + 2] == EOCD_SIGNATURE[1]
          && last[endIdx + 3] == EOCD_SIGNATURE[0]) {

        /*
         * We found a signature. Try to read the EOCD record.
         */

        foundEocdSignature = endIdx;
        ByteBuffer eocdBytes =
            ByteBuffer.wrap(last, foundEocdSignature, last.length - foundEocdSignature);

        try {
          eocd = new Eocd(eocdBytes);
          eocdStart = raf.length() - lastToRead + foundEocdSignature;

          /*
           * Make sure the EOCD takes the whole file up to the end. Log an error if it
           * doesn't.
           */
          if (eocdStart + eocd.getEocdSize() != raf.length()) {
            verifyLog.log(
                "EOCD starts at "
                    + eocdStart
                    + " and has "
                    + eocd.getEocdSize()
                    + " bytes, but file ends at "
                    + raf.length()
                    + ".");
          }
        } catch (IOException e) {
          if (errorFindingSignature != null) {
            e.addSuppressed(errorFindingSignature);
          }

          errorFindingSignature = e;
          foundEocdSignature = -1;
          eocd = null;
        }
      }
    }

    if (foundEocdSignature == -1) {
      throw new IOException(
          "EOCD signature not found in the last " + lastToRead + " bytes of the file.",
          errorFindingSignature);
    }

    Verify.verify(eocdStart >= 0);

    /*
     * Look for the Zip64 central directory locator. If we find it, then this file is a Zip64
     * file and we do not support it.
     */
    long zip64LocatorStart = eocdStart - ZIP64_EOCD_LOCATOR_SIZE;
    if (zip64LocatorStart >= 0) {
      byte[] possibleZip64Locator = new byte[4];
      directFullyRead(zip64LocatorStart, possibleZip64Locator);
      if (LittleEndianUtils.readUnsigned4Le(ByteBuffer.wrap(possibleZip64Locator))
          == ZIP64_EOCD_LOCATOR_SIGNATURE) {
        throw new Zip64NotSupportedException(
            "Zip64 EOCD locator found but Zip64 format is not supported.");
      }
    }

    eocdEntry = map.add(eocdStart, eocdStart + eocd.getEocdSize(), eocd);
  }

  /**
   * Reads the zip's central directory and populates the {@link #directoryEntry} variable. This
   * method can only be called after the EOCD has been read. If the central directory is empty (if
   * there are no files on the zip archive), then {@link #directoryEntry} will be set to {@code
   * null}.
   *
   * @throws IOException failed to read the central directory
   */
  private void readCentralDirectory() throws IOException {
    Preconditions.checkNotNull(eocdEntry, "eocdEntry == null");
    Preconditions.checkNotNull(eocdEntry.getStore(), "eocdEntry.getStore() == null");
    Preconditions.checkState(state != ZipFileState.CLOSED, "state == ZipFileState.CLOSED");
    Preconditions.checkNotNull(raf, "raf == null");
    Preconditions.checkState(directoryEntry == null, "directoryEntry != null");

    Eocd eocd = eocdEntry.getStore();

    long dirSize = eocd.getDirectorySize();
    if (dirSize > Integer.MAX_VALUE) {
      throw new IOException("Cannot read central directory with size " + dirSize + ".");
    }

    long centralDirectoryEnd = eocd.getDirectoryOffset() + dirSize;
    if (centralDirectoryEnd != eocdEntry.getStart()) {
      String msg =
          "Central directory is stored in ["
              + eocd.getDirectoryOffset()
              + " - "
              + (centralDirectoryEnd - 1)
              + "] and EOCD starts at "
              + eocdEntry.getStart()
              + ".";

      /*
       * If there is an empty space between the central directory and the EOCD, we proceed
       * logging an error. If the central directory ends after the start of the EOCD (and
       * therefore, they overlap), throw an exception.
       */
      if (centralDirectoryEnd > eocdEntry.getSize()) {
        throw new IOException(msg);
      } else {
        verifyLog.log(msg);
      }
    }

    byte[] directoryData = new byte[Ints.checkedCast(dirSize)];
    directFullyRead(eocd.getDirectoryOffset(), directoryData);

    CentralDirectory directory =
        CentralDirectory.makeFromData(
            ByteBuffer.wrap(directoryData), eocd.getTotalRecords(), this, storage);
    if (eocd.getDirectorySize() > 0) {
      directoryEntry =
          map.add(
              eocd.getDirectoryOffset(),
              eocd.getDirectoryOffset() + eocd.getDirectorySize(),
              directory);
    }
  }

  /**
   * Opens a portion of the zip for reading. The zip must be open for this method to be invoked.
   * Note that if the zip has not been updated, the individual zip entries may not have been written
   * yet.
   *
   * @param start the index within the zip file to start reading
   * @param end the index within the zip file to end reading (the actual byte pointed by
   *     <em>end</em> will not be read)
   * @return a stream that will read the portion of the file; no decompression is done, data is
   *     returned <em>as is</em>
   * @throws IOException failed to open the zip file
   */
  public InputStream directOpen(final long start, final long end) throws IOException {
    Preconditions.checkState(state != ZipFileState.CLOSED, "state == ZipFileState.CLOSED");
    Preconditions.checkNotNull(raf, "raf == null");
    Preconditions.checkArgument(start >= 0, "start < 0");
    Preconditions.checkArgument(end >= start, "end < start");
    Preconditions.checkArgument(end <= raf.length(), "end > raf.length()");

    return new InputStream() {
      private long mCurr = start;

      @Override
      public int read() throws IOException {
        if (mCurr == end) {
          return -1;
        }

        byte[] b = new byte[1];
        int r = directRead(mCurr, b);
        if (r > 0) {
          mCurr++;
          return b[0];
        } else {
          return -1;
        }
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        Preconditions.checkNotNull(b, "b == null");
        Preconditions.checkArgument(off >= 0, "off < 0");
        Preconditions.checkArgument(off <= b.length, "off > b.length");
        Preconditions.checkArgument(len >= 0, "len < 0");
        Preconditions.checkArgument(off + len <= b.length, "off + len > b.length");

        long availableToRead = end - mCurr;
        long toRead = Math.min(len, availableToRead);

        if (toRead == 0) {
          return -1;
        }

        if (toRead > Integer.MAX_VALUE) {
          throw new IOException("Cannot read " + toRead + " bytes.");
        }

        int r = directRead(mCurr, b, off, Ints.checkedCast(toRead));
        if (r > 0) {
          mCurr += r;
        }

        return r;
      }
    };
  }

  /**
   * Deletes an entry from the zip. This method does not actually delete anything on disk. It just
   * changes in-memory structures. Use {@link #update()} to update the contents on disk.
   *
   * @param entry the entry to delete
   * @param notify should listeners be notified of the deletion? This will only be {@code false} if
   *     the entry is being removed as part of a replacement
   * @throws IOException failed to delete the entry
   * @throws IllegalStateException if open in read-only mode
   */
  void delete(final StoredEntry entry, boolean notify) throws IOException {
    checkNotInReadOnlyMode();

    String path = entry.getCentralDirectoryHeader().getName();
    FileUseMapEntry<StoredEntry> mapEntry = entries.get(path);
    Preconditions.checkNotNull(mapEntry, "mapEntry == null");
    Preconditions.checkArgument(entry == mapEntry.getStore(), "entry != mapEntry.getStore()");

    dirty = true;

    map.remove(mapEntry);
    entries.remove(path);

    if (notify) {
      notify(ext -> ext.removed(entry));
    }
  }

  /**
   * Checks that the file is not in read-only mode.
   *
   * @throws IllegalStateException if the file is in read-only mode
   */
  private void checkNotInReadOnlyMode() {
    if (readOnly) {
      throw new IllegalStateException("Illegal operation in read only model");
    }
  }

  /**
   * Updates the file writing new entries and removing deleted entries. This will force reopening
   * the file as read/write if the file wasn't open in read/write mode.
   *
   * @throws IOException failed to update the file; this exception may have been thrown by the
   *     compressor but only reported here
   */
  public void update() throws IOException {
    checkNotInReadOnlyMode();

    /*
     * Process all background stuff before calling in the extensions.
     */
    processAllReadyEntriesWithWait();
    notify(ZFileExtension::beforeUpdate);

    /*
     * Process all background stuff that may be leftover by the extensions.
     */
    processAllReadyEntriesWithWait();

    if (dirty) {
      writeAllFilesToZip();
    }

    // Even if no files were modified, we still need to recompute the central directory and EOCD
    // in case they have been modified by any extension.
    recomputeAndWriteCentralDirectoryAndEocd();

    // If there are no changes to the file, we may get here without even opening the zip as a
    // RandomAccessFile. In that case, don't try to change the size since we're sure there are no
    // changes.
    if (raf != null) {
      // Ensure we make the zip have the right size (only useful if shrinking), mark the zip as
      // no longer dirty and notify all extensions.
      if (raf.length() != map.size()) {
        raf.setLength(map.size());
      }
    }

    // Regardless of whether the zip was dirty or not, we're sure it isn't now.
    dirty = false;

    notify(
        ext -> {
          ext.updated();
          return null;
        });
  }

  /**
   * Writes all files to the zip, sorting/packing if necessary. The central directory and EOCD are
   * deleted. When this method finishes, all entries have been written to the file and are properly
   * aligned.
   */
  private void writeAllFilesToZip() throws IOException {
    reopenRw();

    /*
     * At this point, no more files can be added. We may need to repack to remove extra
     * empty spaces or sort. If we sort, we don't need to repack as sorting forces the
     * zip file to be as compact as possible.
     */
    if (autoSortFiles) {
      sortZipContents();
    } else {
      packIfNecessary();
    }

    /*
     * We're going to change the file so delete the central directory and the EOCD as they
     * will have to be rewritten.
     */
    deleteDirectoryAndEocd();
    map.truncate();

    /*
     * If we need to use the extra field to cover empty spaces, we do the processing here.
     */
    if (coverEmptySpaceUsingExtraField) {

      /* We will go over all files in the zip and check whether there is empty space before
       * them. If there is, then we will move the entry to the beginning of the empty space
       * (covering it) and extend the extra field with the size of the empty space.
       */
      for (FileUseMapEntry<StoredEntry> entry : new HashSet<>(entries.values())) {
        StoredEntry storedEntry = entry.getStore();
        Preconditions.checkNotNull(storedEntry, "Entry at %s is null", entry.getStart());

        FileUseMapEntry<?> before = map.before(entry);
        if (before == null || !before.isFree()) {
          continue;
        }

        /*
         * We have free space before the current entry. However, we do know that it can
         * be covered by the extra field, because both sortZipContents() and
         * packIfNecessary() guarantee it.
         */
        int localExtraSize =
            storedEntry.getLocalExtra().size() + Ints.checkedCast(before.getSize());
        Verify.verify(localExtraSize <= MAX_LOCAL_EXTRA_FIELD_CONTENTS_SIZE);

        /*
         * Move file back in the zip.
         */
        storedEntry.loadSourceIntoMemory();

        long newStart = before.getStart();
        long newSize = entry.getSize() + before.getSize();

        /*
         * Remove the entry.
         */
        String name = storedEntry.getCentralDirectoryHeader().getName();
        map.remove(entry);
        Verify.verify(entry == entries.remove(name));

        /*
         * Make a list will all existing segments in the entry's extra field, but remove
         * the alignment field, if it exists. Also, sum the size of all kept extra field
         * segments.
         */
        ImmutableList<ExtraField.Segment> currentSegments;
        try {
          currentSegments = storedEntry.getLocalExtra().getSegments();
        } catch (IOException e) {
          /*
           * Parsing current segments has failed. This means the contents of the extra
           * field are not valid. We'll continue discarding the existing segments.
           */
          currentSegments = ImmutableList.of();
        }

        List<ExtraField.Segment> extraFieldSegments = new ArrayList<>();
        int newExtraFieldSize = 0;
        for (ExtraField.Segment segment : currentSegments) {
          if (segment.getHeaderId() != ExtraField.ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID) {
            extraFieldSegments.add(segment);
            newExtraFieldSize += segment.size();
          }
        }

        int spaceToFill =
            Ints.checkedCast(
                before.getSize() + storedEntry.getLocalExtra().size() - newExtraFieldSize);

        extraFieldSegments.add(
            new ExtraField.AlignmentSegment(chooseAlignment(storedEntry), spaceToFill));

        storedEntry.setLocalExtraNoNotify(new ExtraField(ImmutableList.copyOf(extraFieldSegments)));
        entries.put(name, map.add(newStart, newStart + newSize, storedEntry));

        /*
         * Reset the offset to force the file to be rewritten.
         */
        storedEntry.getCentralDirectoryHeader().setOffset(-1);
      }
    }

    /*
     * Write new files in the zip. We identify new files because they don't have an offset
     * in the zip where they are written although we already know, by their location in the
     * file map, where they will be written to.
     *
     * Before writing the files, we sort them in the order they are written in the file so that
     * writes are made in order on disk.
     * This is, however, unlikely to optimize anything relevant given the way the Operating
     * System does caching, but it certainly won't hurt :)
     */
    TreeMap<FileUseMapEntry<?>, StoredEntry> toWriteToStore =
        new TreeMap<>(FileUseMapEntry.COMPARE_BY_START);

    for (FileUseMapEntry<StoredEntry> entry : entries.values()) {
      StoredEntry entryStore = entry.getStore();
      Preconditions.checkNotNull(entryStore, "Entry at %s is null", entry.getStart());
      if (entryStore.getCentralDirectoryHeader().getOffset() == -1) {
        toWriteToStore.put(entry, entryStore);
      }
    }

    /*
     * Add all free entries to the set.
     */
    for (FileUseMapEntry<?> freeArea : map.getFreeAreas()) {
      toWriteToStore.put(freeArea, null);
    }

    /*
     * Write everything to file.
     */
    byte[] chunk = new byte[IO_BUFFER_SIZE];
    for (FileUseMapEntry<?> fileUseMapEntry : toWriteToStore.keySet()) {
      StoredEntry entry = toWriteToStore.get(fileUseMapEntry);
      if (entry == null) {
        int size = Ints.checkedCast(fileUseMapEntry.getSize());
        directWrite(fileUseMapEntry.getStart(), new byte[size]);
      } else {
        writeEntry(entry, fileUseMapEntry.getStart(), chunk);
      }
    }
  }

  /**
   * Recomputes the central directory and EOCD and notifies extensions that all entries have been
   * written. Extensions may further modify the archive and this may require the directory and EOCD
   * to be recomputed several times.
   *
   * <p>This method finishes when the central directory and EOCD have both been computed and written
   * to the zip file and all extensions have been notified using {@link
   * ZFileExtension#entriesWritten()}.
   */
  private void recomputeAndWriteCentralDirectoryAndEocd() throws IOException {
    boolean changedAnything = false;
    boolean hasCentralDirectory;
    int extensionBugDetector = MAXIMUM_EXTENSION_CYCLE_COUNT;
    do {
      // Try to compute the central directory and EOCD. Computing the central directory may end
      // with directoryEntry == null if there are no entries in the zip.
      if (directoryEntry == null) {
        reopenRw();
        changedAnything = true;
        computeCentralDirectory();
      }

      if (eocdEntry == null) {
        // It is fine to call computeEocd even if directoryEntry == null as long as the zip has
        // no files.
        reopenRw();
        changedAnything = true;
        computeEocd();
      }

      hasCentralDirectory = (directoryEntry != null);

      notify(
          ext -> {
            ext.entriesWritten();
            return null;
          });

      if ((--extensionBugDetector) == 0) {
        throw new IOException(
            "Extensions keep resetting the central directory. This is " + "probably a bug.");
      }
    } while ((hasCentralDirectory && directoryEntry == null) || eocdEntry == null);

    if (changedAnything) {
      reopenRw();
      appendCentralDirectory();
      appendEocd();
    }
  }

  /**
   * Reorganizes the zip so that there are no gaps between files bigger than {@link
   * #MAX_LOCAL_EXTRA_FIELD_CONTENTS_SIZE} if {@link #coverEmptySpaceUsingExtraField} is set to
   * {@code true}.
   *
   * <p>Essentially, this makes sure we can cover any empty space with the extra field, given that
   * the local extra field is limited to {@link #MAX_LOCAL_EXTRA_FIELD_CONTENTS_SIZE}. If an entry
   * is too far from the previous one, it is removed and re-added.
   *
   * @throws IOException failed to repack
   */
  private void packIfNecessary() throws IOException {
    if (!coverEmptySpaceUsingExtraField) {
      return;
    }

    SortedSet<FileUseMapEntry<StoredEntry>> entriesByLocation =
        new TreeSet<>(FileUseMapEntry.COMPARE_BY_START);
    entriesByLocation.addAll(entries.values());

    for (FileUseMapEntry<StoredEntry> entry : entriesByLocation) {
      StoredEntry storedEntry = entry.getStore();
      Preconditions.checkNotNull(storedEntry, "Entry at %s is null", entry.getStart());

      FileUseMapEntry<?> before = map.before(entry);
      if (before == null || !before.isFree()) {
        continue;
      }

      int localExtraSize = storedEntry.getLocalExtra().size() + Ints.checkedCast(before.getSize());
      if (localExtraSize > MAX_LOCAL_EXTRA_FIELD_CONTENTS_SIZE) {
        /*
         * This entry is too far from the previous one. Remove it and re-add it to the
         * zip file.
         */
        reAdd(storedEntry, PositionHint.LOWEST_OFFSET);
      }
    }
  }

  /**
   * Removes a stored entry from the zip and adds it back again. This will force the entry to be
   * loaded into memory and repositioned in the zip file. It will also mark the archive as being
   * dirty.
   *
   * @param entry the entry
   * @param positionHint hint to where the file should be positioned when re-adding
   * @throws IOException failed to load the entry into memory
   */
  private void reAdd(StoredEntry entry, PositionHint positionHint) throws IOException {
    String name = entry.getCentralDirectoryHeader().getName();
    FileUseMapEntry<StoredEntry> mapEntry = entries.get(name);
    Preconditions.checkNotNull(mapEntry);
    Preconditions.checkState(mapEntry.getStore() == entry);

    entry.loadSourceIntoMemory();

    map.remove(mapEntry);
    entries.remove(name);
    FileUseMapEntry<StoredEntry> positioned = positionInFile(entry, positionHint);
    entries.put(name, positioned);
    dirty = true;
  }

  /**
   * Invoked from {@link StoredEntry} when entry has changed in a way that forces the local header
   * to be rewritten
   *
   * @param entry the entry that changed
   * @param resized was the local header resized?
   * @throws IOException failed to load the entry into memory
   */
  void localHeaderChanged(StoredEntry entry, boolean resized) throws IOException {
    dirty = true;

    if (resized) {
      reAdd(entry, PositionHint.ANYWHERE);
    }
  }

  /** Invoked when the central directory has changed and needs to be rewritten. */
  void centralDirectoryChanged() {
    dirty = true;
    deleteDirectoryAndEocd();
  }

  /** Updates the file and closes it. */
  @Override
  public void close() throws IOException {
    // We need to make sure to release raf, otherwise we end up locking the file on
    // Windows. Use try-with-resources to handle exception suppressing.
    try (Closeable ignored = this::innerClose) {
      if (!readOnly) {
        update();
      }

      storage.close();
    }

    notify(
        ext -> {
          ext.closed();
          return null;
        });
  }

  /**
   * Removes the Central Directory and EOCD from the file. This will free space for new entries as
   * well as allowing the zip file to be truncated if files have been removed.
   *
   * <p>This method does not mark the zip as dirty.
   */
  private void deleteDirectoryAndEocd() {
    if (directoryEntry != null) {
      map.remove(directoryEntry);
      directoryEntry = null;
    }

    if (eocdEntry != null) {
      map.remove(eocdEntry);

      Eocd eocd = eocdEntry.getStore();
      Verify.verify(eocd != null);
      eocdComment = eocd.getComment();
      eocdEntry = null;
    }
  }

  /**
   * Writes an entry's data in the zip file. This includes everything: the local header and the data
   * itself. After writing, the entry is updated with the offset and its source replaced with a
   * source that reads from the zip file.
   *
   * @param entry the entry to write
   * @param offset the offset at which the entry should be written
   * @throws IOException failed to write the entry
   */
  private void writeEntry(StoredEntry entry, long offset, byte[] chunk) throws IOException {
    Preconditions.checkArgument(
        entry.getDataDescriptorType() == DataDescriptorType.NO_DATA_DESCRIPTOR,
        "Cannot write entries with a data " + "descriptor.");
    Preconditions.checkNotNull(raf, "raf == null");
    Preconditions.checkState(state == ZipFileState.OPEN_RW, "state != ZipFileState.OPEN_RW");

    int r;
    // Put header data to the beginning of buffer
    // LSPatch: write extra entries in the extra field if it's a linking
    int localHeaderSize = entry.getLocalHeaderSize();
    for (var segment : entry.getLocalExtra().getSegments()) {
      if (segment instanceof ExtraField.LinkingEntrySegment) {
        ((ExtraField.LinkingEntrySegment) segment).setOffset(localHeaderSize, offset);
      }
    }
    int readOffset = entry.toHeaderData(chunk);
    assert localHeaderSize == readOffset;
    long writeOffset = offset;
    try (InputStream is = entry.getSource().getRawByteSource().openStream()) {
      while ((r = is.read(chunk, readOffset, chunk.length - readOffset)) >= 0 || readOffset > 0) {
        int toWrite = (r == -1 ? 0 : r) + readOffset;
        directWrite(writeOffset, chunk, 0, toWrite);
        writeOffset += toWrite;
        readOffset = 0;
      }
    }

    /*
     * Set the entry's offset and create the entry source.
     */
    entry.replaceSourceFromZip(offset);
  }

  /**
   * Computes the central directory. The central directory must not have been computed yet. When
   * this method finishes, the central directory has been computed {@link #directoryEntry}, unless
   * the directory is empty in which case {@link #directoryEntry} is left as {@code null}. Nothing
   * is written to disk as a result of this method's invocation.
   *
   * @throws IOException failed to append the central directory
   */
  private void computeCentralDirectory() throws IOException {
    Preconditions.checkState(state == ZipFileState.OPEN_RW, "state != ZipFileState.OPEN_RW");
    Preconditions.checkNotNull(raf, "raf == null");
    Preconditions.checkState(directoryEntry == null, "directoryEntry != null");

    Set<StoredEntry> newStored = Sets.newHashSet();
    for (FileUseMapEntry<StoredEntry> mapEntry : entries.values()) {
      newStored.add(mapEntry.getStore());
    }

    newStored.addAll(linkingEntries);

    /*
     * Make sure we truncate the map before computing the central directory's location since
     * the central directory is the last part of the file.
     */
    map.truncate();

    CentralDirectory newDirectory = CentralDirectory.makeFromEntries(newStored, this);
    byte[] newDirectoryBytes = newDirectory.toBytes();
    long directoryOffset = map.size() + extraDirectoryOffset;

    map.extend(directoryOffset + newDirectoryBytes.length);

    if (newDirectoryBytes.length > 0) {
      directoryEntry =
          map.add(directoryOffset, directoryOffset + newDirectoryBytes.length, newDirectory);
    }
  }

  /**
   * Writes the central directory to the end of the zip file. {@link #directoryEntry} may be {@code
   * null} only if there are no files in the archive.
   *
   * @throws IOException failed to append the central directory
   */
  private void appendCentralDirectory() throws IOException {
    Preconditions.checkState(state == ZipFileState.OPEN_RW, "state != ZipFileState.OPEN_RW");
    Preconditions.checkNotNull(raf, "raf == null");

    if (entries.isEmpty()) {
      Preconditions.checkState(directoryEntry == null, "directoryEntry != null");
      return;
    }

    Preconditions.checkNotNull(directoryEntry, "directoryEntry != null");

    CentralDirectory newDirectory = directoryEntry.getStore();
    Preconditions.checkNotNull(newDirectory, "newDirectory != null");

    byte[] newDirectoryBytes = newDirectory.toBytes();
    long directoryOffset = directoryEntry.getStart();

    /*
     * It is fine to seek beyond the end of file. Seeking beyond the end of file will not extend
     * the file. Even if we do not have any directory data to write, the extend() call below
     * will force the file to be extended leaving exactly extraDirectoryOffset bytes empty at
     * the beginning.
     */
    directWrite(directoryOffset, newDirectoryBytes);
  }

  /**
   * Obtains the byte array representation of the central directory. The central directory must have
   * been already computed. If there are no entries in the zip, the central directory will be empty.
   *
   * @return the byte representation, or an empty array if there are no entries in the zip
   * @throws IOException failed to compute the central directory byte representation
   */
  public byte[] getCentralDirectoryBytes() throws IOException {
    if (entries.isEmpty()) {
      Preconditions.checkState(directoryEntry == null, "directoryEntry != null");
      return new byte[0];
    }

    Preconditions.checkNotNull(directoryEntry, "directoryEntry == null");

    CentralDirectory cd = directoryEntry.getStore();
    Preconditions.checkNotNull(cd, "cd == null");
    return cd.toBytes();
  }

  /**
   * Computes the EOCD. This creates a new {@link #eocdEntry}. The central directory must already be
   * written. If {@link #directoryEntry} is {@code null}, then the zip file must not have any
   * entries.
   *
   * @throws IOException failed to write the EOCD
   */
  private void computeEocd() throws IOException {
    Preconditions.checkState(state == ZipFileState.OPEN_RW, "state != ZipFileState.OPEN_RW");
    Preconditions.checkNotNull(raf, "raf == null");
    if (directoryEntry == null) {
      Preconditions.checkState(entries.isEmpty(), "directoryEntry == null && !entries.isEmpty()");
    }

    long dirStart;
    long dirSize = 0;

    if (directoryEntry != null) {
      CentralDirectory directory = directoryEntry.getStore();

      Preconditions.checkNotNull(directory, "Central directory is null");

      dirStart = directoryEntry.getStart();
      dirSize = directoryEntry.getSize();
      Verify.verify(directory.getEntries().size() == entries.size() + linkingEntries.size());
    } else {
      /*
       * If we do not have a directory, then we must leave any requested offset empty.
       */
      dirStart = extraDirectoryOffset;
    }

    Verify.verify(eocdComment != null);
    Eocd eocd = new Eocd(entries.size() + linkingEntries.size(), dirStart, dirSize, eocdComment);
    eocdComment = null;

    byte[] eocdBytes = eocd.toBytes();
    long eocdOffset = map.size();

    map.extend(eocdOffset + eocdBytes.length);

    eocdEntry = map.add(eocdOffset, eocdOffset + eocdBytes.length, eocd);
  }

  /**
   * Writes the EOCD to the end of the zip file. This creates a new {@link #eocdEntry}. The central
   * directory must already be written. If {@link #directoryEntry} is {@code null}, then the zip
   * file must not have any entries.
   *
   * @throws IOException failed to write the EOCD
   */
  private void appendEocd() throws IOException {
    Preconditions.checkState(state == ZipFileState.OPEN_RW, "state != ZipFileState.OPEN_RW");
    Preconditions.checkNotNull(raf, "raf == null");
    Preconditions.checkNotNull(eocdEntry, "eocdEntry == null");

    Eocd eocd = eocdEntry.getStore();
    Preconditions.checkNotNull(eocd, "eocd == null");

    byte[] eocdBytes = eocd.toBytes();
    long eocdOffset = eocdEntry.getStart();

    directWrite(eocdOffset, eocdBytes);
  }

  /**
   * Obtains the byte array representation of the EOCD. The EOCD must have already been computed for
   * this method to be invoked.
   *
   * @return the byte representation of the EOCD
   * @throws IOException failed to obtain the byte representation of the EOCD
   */
  public byte[] getEocdBytes() throws IOException {
    Preconditions.checkNotNull(eocdEntry, "eocdEntry == null");

    Eocd eocd = eocdEntry.getStore();
    Preconditions.checkNotNull(eocd, "eocd == null");
    return eocd.toBytes();
  }

  /**
   * Closes the file, if it is open.
   *
   * @throws IOException failed to close the file
   */
  private void innerClose() throws IOException {
    if (state == ZipFileState.CLOSED) {
      return;
    }

    Verify.verifyNotNull(raf, "raf == null");

    raf.close();
    raf = null;
    state = ZipFileState.CLOSED;
    if (closedControl == null) {
      closedControl = new CachedFileContents<>(file);
    }

    closedControl.closed(null);
  }

  /**
   * If the zip file is closed, opens it in read-only mode. If it is already open, does nothing. In
   * general, it is not necessary to directly invoke this method. However, if directly reading the
   * zip file using, for example {@link #directRead(long, byte[])}, then this method needs to be
   * called.
   *
   * @throws IOException failed to open the file
   */
  public void openReadOnlyIfClosed() throws IOException {
    if (state != ZipFileState.CLOSED) {
      return;
    }

    state = ZipFileState.OPEN_RO;
    raf = new RandomAccessFile(file, "r");
  }

  /**
   * Opens (or reopens) the zip file as read-write. This method will ensure that {@link #raf} is not
   * null and open for writing.
   *
   * @throws IOException failed to open the file, failed to close it or the file was closed and has
   *     been modified outside the control of this object
   */
  private void reopenRw() throws IOException {
    // We an never open a file RW in read-only mode. We should never get this far, though.
    Verify.verify(!readOnly);

    if (state == ZipFileState.OPEN_RW) {
      return;
    }

    boolean wasClosed;
    if (state == ZipFileState.OPEN_RO) {
      /*
       * ReadAccessFile does not have a way to reopen as RW so we have to close it and
       * open it again.
       */
      innerClose();
      wasClosed = false;
    } else {
      wasClosed = true;
    }

    Verify.verify(state == ZipFileState.CLOSED, "state != ZpiFileState.CLOSED");
    Verify.verify(raf == null, "raf != null");

    if (closedControl != null && !closedControl.isValid()) {
      throw new IOException(
          "File '"
              + file.getAbsolutePath()
              + "' has been modified "
              + "by an external application.");
    }

    raf = new RandomAccessFile(file, "rw");
    state = ZipFileState.OPEN_RW;

    /*
     * Now that we've open the zip and are ready to write, clear out any data descriptors
     * in the zip since we don't need them and they take space in the archive.
     */
    for (StoredEntry entry : entries()) {
      dirty |= entry.removeDataDescriptor();
    }

    if (wasClosed) {
      notify(ZFileExtension::open);
    }
  }

  /**
   * Equivalent to call {@link #add(String, InputStream, boolean)} using {@code true} as {@code
   * mayCompress}.
   *
   * @param name the file name (<em>i.e.</em>, path); paths should be defined using slashes and the
   *     name should not end in slash
   * @param stream the source for the file's data
   * @throws IOException failed to read the source data
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void add(String name, InputStream stream) throws IOException {
    checkNotInReadOnlyMode();
    add(name, stream, true);
  }

  /**
   * Adds a file to the archive.
   *
   * <p>Adding the file will not update the archive immediately. Updating will only happen when the
   * {@link #update()} method is invoked.
   *
   * <p>Adding a file with the same name as an existing file will replace that file in the archive.
   *
   * @param name the file name (<em>i.e.</em>, path); paths should be defined using slashes and the
   *     name should not end in slash
   * @param stream the source for the file's data
   * @param mayCompress can the file be compressed? This flag will be ignored if the alignment rules
   *     force the file to be aligned, in which case the file will not be compressed.
   * @throws IOException failed to read the source data
   * @throws IllegalStateException if the file is in read-only mode
   */
  public StoredEntry add(String name, InputStream stream, boolean mayCompress) throws IOException {
    return add(name, storage.fromStream(stream), mayCompress);
  }

  /**
   * Adds a file to the archive.
   *
   * <p>Adding the file will not update the archive immediately. Updating will only happen when the
   * {@link #update()} method is invoked.
   *
   * <p>Adding a file with the same name as an existing file will replace that file in the archive.
   *
   * @param name the file name (<em>i.e.</em>, path); paths should be defined using slashes and the
   *     name should not end in slash
   * @param source the source for the file's data
   * @param mayCompress can the file be compressed? This flag will be ignored if the alignment rules
   *     force the file to be aligned, in which case the file will not be compressed.
   * @throws IOException failed to read the source data
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void add(String name, ByteSource source, boolean mayCompress) throws IOException {
    Optional<Long> sizeBytes = source.sizeIfKnown();
    if (!sizeBytes.isPresent()) {
      throw new IllegalArgumentException("Can only add ByteSources with known size");
    }
    add(name, new CloseableDelegateByteSource(source, sizeBytes.get()), mayCompress);
  }

  private StoredEntry add(String name, CloseableByteSource source, boolean mayCompress)
      throws IOException {
    checkNotInReadOnlyMode();

    /*
     * Clean pending background work, if needed.
     */
    processAllReadyEntries();

    return add(makeStoredEntry(name, source, mayCompress));
  }

  public void addLink(StoredEntry linkedEntry, String dstName)
          throws IOException {
      addNestedLink(linkedEntry, dstName, null, 0L, false);
  }

  void addNestedLink(StoredEntry linkedEntry, String dstName, StoredEntry nestedEntry, long nestedOffset, boolean dummy)
          throws IOException {
    Preconditions.checkArgument(linkedEntry != null, "linkedEntry is null");
    Preconditions.checkArgument(linkedEntry.getCentralDirectoryHeader().getOffset() < 0, "linkedEntry is not new file");
    Preconditions.checkArgument(!linkedEntry.isLinkingEntry(), "linkedEntry is a linking entry");
    var linkingEntry = new StoredEntry(dstName, this, storage, linkedEntry, nestedEntry, nestedOffset, dummy);
    linkingEntries.add(linkingEntry);
    linkedEntry.setLocalExtraNoNotify(new ExtraField(ImmutableList.<ExtraField.Segment>builder().add(linkedEntry.getLocalExtra().getSegments().toArray(new ExtraField.Segment[0])).add(new ExtraField.LinkingEntrySegment(linkingEntry)).build()));
    reAdd(linkedEntry, PositionHint.LOWEST_OFFSET);
  }

  public NestedZip addNestedZip(NestedZip.NameCallback name, File src, boolean mayCompress) throws IOException {
    return new NestedZip(name, this, src, mayCompress);
  }


  /**
   * Adds a {@link StoredEntry} to the zip. The entry is not immediately added to {@link #entries}
   * because data may not yet be available. Instead, it is placed under {@link #uncompressedEntries}
   * and later moved to {@link #processAllReadyEntries()} when done.
   *
   * <p>This method invokes {@link #processAllReadyEntries()} to move the entry if it has already
   * been computed so, if there is no delay in compression, and no more files are in waiting queue,
   * then the entry is added to {@link #entries} immediately.
   *
   * @param newEntry the entry to add
   * @throws IOException failed to process this entry (or a previous one whose future only completed
   *     now)
   */
  private StoredEntry add(final StoredEntry newEntry) throws IOException {
    uncompressedEntries.add(newEntry);
    processAllReadyEntries();
    return newEntry;
  }

  /**
   * Creates a stored entry. This does not add the entry to the zip file, it just creates the {@link
   * StoredEntry} object.
   *
   * @param name the name of the entry
   * @param source the source with the entry's data
   * @param mayCompress can the entry be compressed?
   * @return the created entry
   * @throws IOException failed to create the entry
   */
  private StoredEntry makeStoredEntry(String name, CloseableByteSource source, boolean mayCompress)
      throws IOException {
    long crc32 = source.hash(Hashing.crc32()).padToLong();

    boolean encodeWithUtf8 = !EncodeUtils.canAsciiEncode(name);

    SettableFuture<CentralDirectoryHeaderCompressInfo> compressInfo = SettableFuture.create();
    GPFlags flags = GPFlags.make(encodeWithUtf8);
    CentralDirectoryHeader newFileData =
        new CentralDirectoryHeader(
            name, EncodeUtils.encode(name, flags), source.size(), compressInfo, flags, this);
    newFileData.setCrc32(crc32);

    /*
     * Create the new entry and sets its data source. Offset should be set to -1 automatically
     * because this is a new file. With offset set to -1, StoredEntry does not try to verify the
     * local header. Since this is a new file, there is no local header and not checking it is
     * what we want to happen.
     */
    Verify.verify(newFileData.getOffset() == -1);
    return new StoredEntry(
        newFileData, this, createSources(mayCompress, source, compressInfo, newFileData), storage);
  }

  /**
   * Creates the processed and raw sources for an entry.
   *
   * @param mayCompress can the entry be compressed?
   * @param source the entry's data (uncompressed)
   * @param compressInfo the compression info future that will be set when the raw entry is created
   *     and the {@link CentralDirectoryHeaderCompressInfo} object can be created
   * @param newFileData the central directory header for the new file
   * @return the sources whose data may or may not be already defined
   * @throws IOException failed to create the raw sources
   */
  private ProcessedAndRawByteSources createSources(
      boolean mayCompress,
      CloseableByteSource source,
      SettableFuture<CentralDirectoryHeaderCompressInfo> compressInfo,
      CentralDirectoryHeader newFileData)
      throws IOException {
    if (mayCompress) {
      ListenableFuture<CompressionResult> result = compressor.compress(source, storage);
      Futures.addCallback(
          result,
          new FutureCallback<CompressionResult>() {
            @Override
            public void onSuccess(CompressionResult result) {
              compressInfo.set(
                  new CentralDirectoryHeaderCompressInfo(
                      newFileData, result.getCompressionMethod(), result.getSize()));
            }

            @Override
            public void onFailure(Throwable t) {
              compressInfo.setException(t);
            }
          },
          MoreExecutors.directExecutor());

      ListenableFuture<CloseableByteSource> compressedByteSourceFuture =
          Futures.transform(result, CompressionResult::getSource, MoreExecutors.directExecutor());
      LazyDelegateByteSource compressedByteSource =
          new LazyDelegateByteSource(compressedByteSourceFuture);
      return new ProcessedAndRawByteSources(source, compressedByteSource);
    } else {
      compressInfo.set(
          new CentralDirectoryHeaderCompressInfo(
              newFileData, CompressionMethod.STORE, source.size()));
      return new ProcessedAndRawByteSources(source, source);
    }
  }

  /**
   * Moves all ready entries from {@link #uncompressedEntries} to {@link #entries}. It will stop as
   * soon as entry whose future has not been completed is found.
   *
   * @throws IOException the exception reported in the future computation, if any, or failed to add
   *     a file to the archive
   */
  private void processAllReadyEntries() throws IOException {
    /*
     * Many things can happen during addToEntries(). Because addToEntries() fires
     * notifications to extensions, other files can be added, removed, etc. Ee are *not*
     * guaranteed that new stuff does not get into uncompressedEntries: add() will still work
     * and will add new entries in there.
     *
     * However -- important -- processReadyEntries() may be invoked during addToEntries()
     * because of the extension mechanism. This means that stuff *can* be removed from
     * uncompressedEntries and moved to entries during addToEntries().
     */
    while (!uncompressedEntries.isEmpty()) {
      StoredEntry next = uncompressedEntries.get(0);
      CentralDirectoryHeader cdh = next.getCentralDirectoryHeader();
      Future<CentralDirectoryHeaderCompressInfo> compressionInfo = cdh.getCompressionInfo();
      if (!compressionInfo.isDone()) {
        /*
         * First entry in queue is not yet complete. We can't do anything else.
         */
        return;
      }

      uncompressedEntries.remove(0);

      try {
        compressionInfo.get();
      } catch (InterruptedException e) {
        throw new IOException(
            "Impossible I/O exception: get for already computed "
                + "future throws InterruptedException",
            e);
      } catch (ExecutionException e) {
        throw new IOException("Failed to obtain compression information for entry", e);
      }

      addToEntries(next);
    }
  }

  /**
   * Waits until {@link #uncompressedEntries} is empty.
   *
   * @throws IOException the exception reported in the future computation, if any, or failed to add
   *     a file to the archive
   */
  private void processAllReadyEntriesWithWait() throws IOException {
    processAllReadyEntries();
    while (!uncompressedEntries.isEmpty()) {
      /*
       * Wait for the first future to complete and then try again. Keep looping until we're
       * done.
       */
      StoredEntry first = uncompressedEntries.get(0);
      CentralDirectoryHeader cdh = first.getCentralDirectoryHeader();
      cdh.getCompressionInfoWithWait();

      processAllReadyEntries();
    }
  }

  /**
   * Adds a new file to {@link #entries}. This is actually added to the zip and its space allocated
   * in the {@link #map}.
   *
   * @param newEntry the new entry to add
   * @throws IOException failed to add the file
   */
  private void addToEntries(final StoredEntry newEntry) throws IOException {
    Preconditions.checkArgument(
        newEntry.getDataDescriptorType() == DataDescriptorType.NO_DATA_DESCRIPTOR,
        "newEntry has data descriptor");

    /*
     * If there is a file with the same name in the archive, remove it. We remove it by
     * calling delete() on the entry (this is the public API to remove a file from the archive).
     * StoredEntry.delete() will call {@link ZFile#delete(StoredEntry, boolean)}  to perform
     * data structure cleanup.
     */
    FileUseMapEntry<StoredEntry> toReplace =
        entries.get(newEntry.getCentralDirectoryHeader().getName());
    final StoredEntry replaceStore;
    if (toReplace != null) {
      replaceStore = toReplace.getStore();
      Preconditions.checkNotNull(
          replaceStore, "File to replace at %s is null", toReplace.getStart());
      replaceStore.delete(false);
    } else {
      replaceStore = null;
    }

    FileUseMapEntry<StoredEntry> fileUseMapEntry = positionInFile(newEntry, PositionHint.ANYWHERE);
    entries.put(newEntry.getCentralDirectoryHeader().getName(), fileUseMapEntry);

    dirty = true;

    notify(ext -> ext.added(newEntry, replaceStore));
  }

  /**
   * Finds a location in the zip where this entry will be added to and create the map entry. This
   * method cannot be called if there is already a map entry for the given entry (if you do that,
   * then you're doing something wrong somewhere).
   *
   * <p>This may delete the central directory and EOCD (if it deletes one, it deletes the other) if
   * there is no space before the central directory. Otherwise, the file would be added after the
   * central directory. This would force a new central directory to be written when updating the
   * file and would create a hole in the zip. Me no like holes. Holes are evil.
   *
   * @param entry the entry to place in the zip
   * @param positionHint hint to where the file should be positioned
   * @return the position in the file where the entry should be placed
   */
  private FileUseMapEntry<StoredEntry> positionInFile(StoredEntry entry, PositionHint positionHint)
      throws IOException {
    deleteDirectoryAndEocd();
    long size = entry.getInFileSize();
    int localHeaderSize = entry.getLocalHeaderSize();
    int alignment = chooseAlignment(entry);

    FileUseMap.PositionAlgorithm algorithm;

    switch (positionHint) {
      case LOWEST_OFFSET:
        algorithm = FileUseMap.PositionAlgorithm.FIRST_FIT;
        break;
      case ANYWHERE:
        algorithm = FileUseMap.PositionAlgorithm.BEST_FIT;
        break;
      default:
        throw new AssertionError();
    }

    long newOffset = map.locateFree(size, localHeaderSize, alignment, algorithm);
    long newEnd = newOffset + entry.getInFileSize();
    if (newEnd > map.size()) {
      map.extend(newEnd);
    }

    return map.add(newOffset, newEnd, entry);
  }

  /**
   * Determines what is the alignment value of an entry.
   *
   * @param entry the entry
   * @return the alignment value, {@link AlignmentRule#NO_ALIGNMENT} if there is no alignment
   *     required for the entry
   * @throws IOException failed to determine the alignment
   */
  private int chooseAlignment(StoredEntry entry) throws IOException {
    CentralDirectoryHeader cdh = entry.getCentralDirectoryHeader();
    CentralDirectoryHeaderCompressInfo compressionInfo = cdh.getCompressionInfoWithWait();

    boolean isCompressed = compressionInfo.getMethod() != CompressionMethod.STORE;
    if (isCompressed) {
      return AlignmentRule.NO_ALIGNMENT;
    } else {
      return alignmentRule.alignment(cdh.getName());
    }
  }

  /**
   * Adds all files from another zip file, maintaining their compression. Files specified in
   * <em>src</em> that are already on this file will replace the ones in this file. However, if
   * their sizes and checksums are equal, they will be ignored.
   *
   * <p>This method will not perform any changes in itself, it will only update in-memory data
   * structures. To actually write the zip file, invoke either {@link #update()} or {@link
   * #close()}.
   *
   * @param src the source archive
   * @param ignoreFilter predicate that, if {@code true}, identifies files in <em>src</em> that
   *     should be ignored by merging; merging will behave as if these files were not there
   * @throws IOException failed to read from <em>src</em> or write on the output
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void mergeFrom(ZFile src, Predicate<String> ignoreFilter) throws IOException {
    checkNotInReadOnlyMode();

    for (StoredEntry fromEntry : src.entries()) {
      if (ignoreFilter.apply(fromEntry.getCentralDirectoryHeader().getName())) {
        continue;
      }

      boolean replaceCurrent = true;
      String path = fromEntry.getCentralDirectoryHeader().getName();
      FileUseMapEntry<StoredEntry> currentEntry = entries.get(path);

      if (currentEntry != null) {
        long fromSize = fromEntry.getCentralDirectoryHeader().getUncompressedSize();
        long fromCrc = fromEntry.getCentralDirectoryHeader().getCrc32();

        StoredEntry currentStore = currentEntry.getStore();
        Preconditions.checkNotNull(currentStore, "Entry at %s is null", currentEntry.getStart());

        long currentSize = currentStore.getCentralDirectoryHeader().getUncompressedSize();
        long currentCrc = currentStore.getCentralDirectoryHeader().getCrc32();

        if (fromSize == currentSize && fromCrc == currentCrc) {
          replaceCurrent = false;
        }
      }

      if (replaceCurrent) {
        CentralDirectoryHeader fromCdr = fromEntry.getCentralDirectoryHeader();
        CentralDirectoryHeaderCompressInfo fromCompressInfo = fromCdr.getCompressionInfoWithWait();
        CentralDirectoryHeader newFileData;
        try {
          /*
           * We make two changes in the central directory from the file to merge:
           * we reset the offset to force the entry to be written and we reset the
           * deferred CRC bit as we don't need the extra stuff after the file. It takes
           * space and is totally useless.
           */
          newFileData = fromCdr.clone();
          newFileData.setOffset(-1);
          newFileData.resetDeferredCrc();
        } catch (CloneNotSupportedException e) {
          throw new IOException("Failed to clone CDR.", e);
        }

        /*
         * Read the data (read directly the compressed source if there is one).
         */
        ProcessedAndRawByteSources fromSource = fromEntry.getSource();
        InputStream fromInput = fromSource.getRawByteSource().openStream();
        long sourceSize = fromSource.getRawByteSource().size();
        if (sourceSize > Integer.MAX_VALUE) {
          throw new IOException("Cannot read source with " + sourceSize + " bytes.");
        }

        byte[] data = new byte[Ints.checkedCast(sourceSize)];
        int read = 0;
        while (read < data.length) {
          int r = fromInput.read(data, read, data.length - read);
          Verify.verify(r >= 0, "There should be at least 'size' bytes in the stream.");
          read += r;
        }

        /*
         * Build the new source and wrap it around an inflater source if data came from
         * a compressed source.
         */
        CloseableByteSource rawContents = storage.fromSource(fromSource.getRawByteSource());
        CloseableByteSource processedContents;
        if (fromCompressInfo.getMethod() == CompressionMethod.DEFLATE) {
          //noinspection IOResourceOpenedButNotSafelyClosed
          processedContents = new InflaterByteSource(rawContents);
        } else {
          processedContents = rawContents;
        }

        ProcessedAndRawByteSources newSource =
            new ProcessedAndRawByteSources(processedContents, rawContents);

        /*
         * Add will replace any current entry with the same name.
         */
        StoredEntry newEntry = new StoredEntry(newFileData, this, newSource, storage);
        add(newEntry);
      }
    }
  }

  /**
   * Forcibly marks this zip file as touched, forcing it to be updated when {@link #update()} or
   * {@link #close()} are invoked.
   *
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void touch() {
    checkNotInReadOnlyMode();
    dirty = true;
  }

  /**
   * Wait for any background tasks to finish and report any errors. In general this method does not
   * need to be invoked directly as errors from background tasks are reported during {@link
   * #add(String, InputStream, boolean)}, {@link #update()} and {@link #close()}. However, if
   * required for some purposes, <em>e.g.</em>, ensuring all notifications have been done to
   * extensions, then this method may be called. It will wait for all background tasks to complete.
   *
   * @throws IOException some background work failed
   */
  public void finishAllBackgroundTasks() throws IOException {
    processAllReadyEntriesWithWait();
  }

  /**
   * Realigns all entries in the zip. This is equivalent to call {@link StoredEntry#realign()} for
   * all entries in the zip file.
   *
   * @return has any entry been changed? Note that for entries that have not yet been written on the
   *     file, realignment does not count as a change as nothing needs to be updated in the file;
   *     entries that have been updated may have been recreated and the existing references outside
   *     of {@code ZFile} may refer to {@link StoredEntry}s that are no longer valid
   * @throws IOException failed to realign the zip; some entries in the zip may have been lost due
   *     to the I/O error
   * @throws IllegalStateException if the file is in read-only mode
   */
  public boolean realign() throws IOException {
    checkNotInReadOnlyMode();

    boolean anyChanges = false;
    for (StoredEntry entry : entries()) {
      anyChanges |= entry.realign();
    }

    if (anyChanges) {
      dirty = true;
    }

    return anyChanges;
  }

  /**
   * Realigns a stored entry, if necessary. Realignment is done by removing and re-adding the file
   * if it was not aligned.
   *
   * @param entry the entry to realign
   * @return has the entry been changed? Note that if the entry has not yet been written on the
   *     file, realignment does not count as a change as nothing needs to be updated in the file
   * @throws IOException failed to read/write an entry; the entry may no longer exist in the file
   */
  boolean realign(StoredEntry entry) throws IOException {
    FileUseMapEntry<StoredEntry> mapEntry =
        entries.get(entry.getCentralDirectoryHeader().getName());
    Verify.verify(entry == mapEntry.getStore());
    long currentDataOffset = mapEntry.getStart() + entry.getLocalHeaderSize();

    int expectedAlignment = chooseAlignment(entry);
    long misalignment = currentDataOffset % expectedAlignment;
    if (misalignment == 0) {
      /*
       * Good. File is aligned properly.
       */
      return false;
    }

    if (entry.getCentralDirectoryHeader().getOffset() == -1) {
      /*
       * File is not aligned but it is not written. We do not really need to do much other
       * than find another place in the map.
       */
      map.remove(mapEntry);
      long newStart =
          map.locateFree(
              mapEntry.getSize(),
              entry.getLocalHeaderSize(),
              expectedAlignment,
              FileUseMap.PositionAlgorithm.BEST_FIT);
      mapEntry = map.add(newStart, newStart + entry.getInFileSize(), entry);
      entries.put(entry.getCentralDirectoryHeader().getName(), mapEntry);

      /*
       * Just for safety. We're modifying the in-memory structures but the file should
       * already be marked as dirty.
       */
      Verify.verify(dirty);

      return false;
    }

    /*
     * Get the entry data source, but check if we have a compressed one (we don't want to
     * inflate and deflate).
     */
    CentralDirectoryHeaderCompressInfo compressInfo =
        entry.getCentralDirectoryHeader().getCompressionInfoWithWait();

    ProcessedAndRawByteSources source = entry.getSource();

    CentralDirectoryHeader clonedCdh;
    try {
      clonedCdh = entry.getCentralDirectoryHeader().clone();
    } catch (CloneNotSupportedException e) {
      Verify.verify(false);
      return false;
    }

    /*
     * We make two changes in the central directory when realigning:
     * we reset the offset to force the entry to be written and we reset the
     * deferred CRC bit as we don't need the extra stuff after the file. It takes
     * space and is totally useless and we may need the extra space to realign the entry...
     */
    clonedCdh.setOffset(-1);
    clonedCdh.resetDeferredCrc();

    CloseableByteSource rawContents = storage.fromSource(source.getRawByteSource());
    CloseableByteSource processedContents;

    if (compressInfo.getMethod() == CompressionMethod.DEFLATE) {
      //noinspection IOResourceOpenedButNotSafelyClosed
      processedContents = new InflaterByteSource(rawContents);
    } else {
      processedContents = rawContents;
    }

    ProcessedAndRawByteSources newSource =
        new ProcessedAndRawByteSources(processedContents, rawContents);

    /*
     * Add the new file. This will replace the existing one.
     */
    StoredEntry newEntry = new StoredEntry(clonedCdh, this, newSource, storage);
    add(newEntry);
    return true;
  }

  /**
   * Adds an extension to this zip file.
   *
   * @param extension the listener to add
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void addZFileExtension(ZFileExtension extension) {
    checkNotInReadOnlyMode();
    extensions.add(extension);
  }

  /**
   * Removes an extension from this zip file.
   *
   * @param extension the listener to remove
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void removeZFileExtension(ZFileExtension extension) {
    checkNotInReadOnlyMode();
    extensions.remove(extension);
  }

  /**
   * Notifies all extensions, collecting their execution requests and running them.
   *
   * @param function the function to apply to all listeners, it will generally invoke the
   *     notification method on the listener and return the result of that invocation
   * @throws IOException failed to process some extensions
   */
  private void notify(IOExceptionFunction<ZFileExtension, IOExceptionRunnable> function)
      throws IOException {
    for (ZFileExtension fl : Lists.newArrayList(extensions)) {
      IOExceptionRunnable r = function.apply(fl);
      if (r != null) {
        toRun.add(r);
      }
    }

    if (!isNotifying) {
      isNotifying = true;

      try {
        while (!toRun.isEmpty()) {
          IOExceptionRunnable r = toRun.remove(0);
          r.run();
        }
      } finally {
        isNotifying = false;
      }
    }
  }

  /**
   * Directly writes data in the zip file. <strong>Incorrect use of this method may corrupt the zip
   * file</strong>. Invoking this method may force the zip to be reopened in read/write mode.
   *
   * @param offset the offset at which data should be written
   * @param data the data to write, may be an empty array
   * @param start start offset in {@code data} where data to write is located
   * @param count number of bytes of data to write
   * @throws IOException failed to write the data
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void directWrite(long offset, byte[] data, int start, int count) throws IOException {
    checkNotInReadOnlyMode();

    Preconditions.checkArgument(offset >= 0, "offset < 0");
    Preconditions.checkArgument(start >= 0, "start >= 0");
    Preconditions.checkArgument(count >= 0, "count >= 0");

    if (data.length == 0) {
      return;
    }

    Preconditions.checkArgument(start <= data.length, "start > data.length");
    Preconditions.checkArgument(start + count <= data.length, "start + count > data.length");

    reopenRw();
    Preconditions.checkNotNull(raf, "raf == null");

    raf.seek(offset);
    raf.write(data, start, count);
  }

  /**
   * Same as {@code directWrite(offset, data, 0, data.length)}.
   *
   * @param offset the offset at which data should be written
   * @param data the data to write, may be an empty array
   * @throws IOException failed to write the data
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void directWrite(long offset, byte[] data) throws IOException {
    directWrite(offset, data, 0, data.length);
  }

  /**
   * Returns the current size (in bytes) of the underlying file.
   *
   * @throws IOException if an I/O error occurs
   */
  public long directSize() throws IOException {
    /*
     * Only force a reopen if the file is closed.
     */
    if (raf == null) {
      reopenRw();
      Preconditions.checkNotNull(raf, "raf == null");
    }
    return raf.length();
  }

  /**
   * Directly reads data from the zip file. Invoking this method may force the zip to be reopened in
   * read/write mode.
   *
   * @param offset the offset at which data should be written
   * @param data the array where read data should be stored
   * @param start start position in the array where to write data to
   * @param count how many bytes of data can be written
   * @return how many bytes of data have been written or {@code -1} if there are no more bytes to be
   *     read
   * @throws IOException failed to write the data
   */
  public int directRead(long offset, byte[] data, int start, int count) throws IOException {
    Preconditions.checkArgument(start >= 0, "start >= 0");
    Preconditions.checkArgument(count >= 0, "count >= 0");
    Preconditions.checkArgument(start <= data.length, "start > data.length");
    Preconditions.checkArgument(start + count <= data.length, "start + count > data.length");
    return directRead(offset, ByteBuffer.wrap(data, start, count));
  }

  /**
   * Directly reads data from the zip file. Invoking this method may force the zip to be reopened in
   * read/write mode.
   *
   * @param offset the offset from which data should be read
   * @param dest the output buffer to fill with data from the {@code offset}.
   * @return how many bytes of data have been written or {@code -1} if there are no more bytes to be
   *     read
   * @throws IOException failed to write the data
   */
  public int directRead(long offset, ByteBuffer dest) throws IOException {
    Preconditions.checkArgument(offset >= 0, "offset < 0");

    if (!dest.hasRemaining()) {
      return 0;
    }

    /*
     * Only force a reopen if the file is closed.
     */
    if (raf == null) {
      reopenRw();
      Preconditions.checkNotNull(raf, "raf == null");
    }

    raf.seek(offset);
    return raf.getChannel().read(dest);
  }

  /**
   * Same as {@code directRead(offset, data, 0, data.length)}.
   *
   * @param offset the offset at which data should be read
   * @param data receives the read data, may be an empty array
   * @throws IOException failed to read the data
   */
  public int directRead(long offset, byte[] data) throws IOException {
    return directRead(offset, data, 0, data.length);
  }

  /**
   * Reads exactly {@code data.length} bytes of data, failing if it was not possible to read all the
   * requested data.
   *
   * @param offset the offset at which to start reading
   * @param data the array that receives the data read
   * @throws IOException failed to read some data or there is not enough data to read
   */
  public void directFullyRead(long offset, byte[] data) throws IOException {
    directFullyRead(offset, ByteBuffer.wrap(data));
  }

  /**
   * Reads exactly {@code dest.remaining()} bytes of data, failing if it was not possible to read
   * all the requested data.
   *
   * @param offset the offset at which to start reading
   * @param dest the output buffer to fill with data
   * @throws IOException failed to read some data or there is not enough data to read
   */
  public void directFullyRead(long offset, ByteBuffer dest) throws IOException {
    Preconditions.checkArgument(offset >= 0, "offset < 0");

    if (!dest.hasRemaining()) {
      return;
    }

    /*
     * Only force a reopen if the file is closed.
     */
    if (raf == null) {
      reopenRw();
      Preconditions.checkNotNull(raf, "raf == null");
    }

    FileChannel fileChannel = raf.getChannel();
    while (dest.hasRemaining()) {
      fileChannel.position(offset);
      int chunkSize = fileChannel.read(dest);
      if (chunkSize == -1) {
        throw new EOFException("Failed to read " + dest.remaining() + " more bytes: premature EOF");
      }
      offset += chunkSize;
    }
  }

  /**
   * Adds all files and directories recursively.
   *
   * <p>Equivalent to calling {@link #addAllRecursively(File, Predicate)} using a predicate that
   * always returns {@code true}
   *
   * @param file a file or directory; if it is a directory, all files and directories will be added
   *     recursively
   * @throws IOException failed to some (or all ) of the files
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void addAllRecursively(File file) throws IOException {
    checkNotInReadOnlyMode();
    addAllRecursively(file, f -> true);
  }

  /**
   * Adds all files and directories recursively.
   *
   * @param file a file or directory; if it is a directory, all files and directories will be added
   *     recursively
   * @param mayCompress a function that decides whether files may be compressed
   * @throws IOException failed to some (or all ) of the files
   * @throws IllegalStateException if the file is in read-only mode
   */
  public void addAllRecursively(File file, Predicate<? super File> mayCompress) throws IOException {
    checkNotInReadOnlyMode();

    addAllRecursively(file, file, mayCompress);
  }

  /**
   * Adds all files and directories recursively.
   *
   * @param file a file or directory; if it is a directory, all files and directories will be added
   *     recursively
   * @param base the file/directory to compute the relative path from
   * @param mayCompress a function that decides whether files may be compressed
   * @throws IOException failed to some (or all ) of the files
   * @throws IllegalStateException if the file is in read-only mode
   */
  private void addAllRecursively(File file, File base, Predicate<? super File> mayCompress)
      throws IOException {
    // If we're just adding a file, do not compute a relative path, but rather use the file name
    // as path.
    String path =
        Objects.equal(file, base)
            ? file.getName()
            : base.toURI().relativize(file.toURI()).getPath();

    /*
     * The case of file.isFile() is different because if file.isFile() we will add it to the
     * zip in the root. However, if file.isDirectory() we won't add it and add its children.
     */
    if (file.isFile()) {
      boolean mayCompressFile = mayCompress.apply(file);

      try (Closer closer = Closer.create()) {
        FileInputStream fileInput = closer.register(new FileInputStream(file));
        add(path, fileInput, mayCompressFile);
      }

      return;
    } else if (file.isDirectory()) {
      // Add an entry for the directory, unless it is the base.
      if (!file.equals(base)) {
        try (Closer closer = Closer.create()) {
          InputStream stream = closer.register(new ByteArrayInputStream(new byte[0]));
          add(path, stream, false);
        }
      }

      // Add recursively.
      File[] directoryContents = file.listFiles();
      if (directoryContents != null) {
        Arrays.sort(directoryContents, (f0, f1) -> f0.getName().compareTo(f1.getName()));
        for (File subFile : directoryContents) {
          addAllRecursively(subFile, base, mayCompress);
        }
      }
    }
  }

  /**
   * Obtains the offset at which the central directory exists, or at which it will be written if the
   * zip file were to be flushed immediately.
   *
   * @return the offset, in bytes, where the central directory is or will be written; this value
   *     includes any extra offset for the central directory
   */
  public long getCentralDirectoryOffset() {
    if (directoryEntry != null) {
      return directoryEntry.getStart();
    }

    /*
     * If there are no entries, the central directory is written at the start of the file.
     */
    if (entries.isEmpty()) {
      return extraDirectoryOffset;
    }

    /*
     * The Central Directory is written after all entries. This will be at the end of the file
     * if the
     */
    return map.usedSize() + extraDirectoryOffset;
  }

  /**
   * Obtains the size of the central directory, if the central directory is written in the zip file.
   *
   * @return the size of the central directory or {@code -1} if the central directory has not been
   *     computed
   */
  public long getCentralDirectorySize() {
    if (directoryEntry != null) {
      return directoryEntry.getSize();
    }

    if (entries.isEmpty()) {
      return 0;
    }

    return 1;
  }

  /**
   * Obtains the offset of the EOCD record, if the EOCD has been written to the file.
   *
   * @return the offset of the EOCD or {@code -1} if none exists yet
   */
  public long getEocdOffset() {
    if (eocdEntry == null) {
      return -1;
    }

    return eocdEntry.getStart();
  }

  /**
   * Obtains the size of the EOCD record, if the EOCD has been written to the file.
   *
   * @return the size of the EOCD of {@code -1} it none exists yet
   */
  public long getEocdSize() {
    if (eocdEntry == null) {
      return -1;
    }

    return eocdEntry.getSize();
  }

  /**
   * Obtains the comment in the EOCD.
   *
   * @return the comment exactly as it was encoded in the EOCD, no encoding conversion is done
   */
  public byte[] getEocdComment() {
    if (eocdEntry == null) {
      Verify.verify(eocdComment != null);
      byte[] eocdCommentCopy = new byte[eocdComment.length];
      System.arraycopy(eocdComment, 0, eocdCommentCopy, 0, eocdComment.length);
      return eocdCommentCopy;
    }

    Eocd eocd = eocdEntry.getStore();
    Verify.verify(eocd != null);
    return eocd.getComment();
  }

  /**
   * Sets the comment in the EOCD.
   *
   * @param comment the new comment; no conversion is done, these exact bytes will be placed in the
   *     EOCD comment
   * @throws IllegalStateException if file is in read-only mode
   */
  public void setEocdComment(byte[] comment) {
    checkNotInReadOnlyMode();

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
      if (comment[i] == EOCD_SIGNATURE[3]
          && comment[i + 1] == EOCD_SIGNATURE[2]
          && comment[i + 2] == EOCD_SIGNATURE[1]
          && comment[i + 3] == EOCD_SIGNATURE[0]) {
        // We found a possible EOCD signature at position i. Try to read it.
        ByteBuffer bytes = ByteBuffer.wrap(comment, i, comment.length - i);
        try {
          new Eocd(bytes);
          throw new IllegalArgumentException(
              "Position " + i + " of the comment contains a valid EOCD record.");
        } catch (IOException e) {
          // Fine, this is an invalid record. Move along...
        }
      }
    }

    deleteDirectoryAndEocd();
    eocdComment = new byte[comment.length];
    System.arraycopy(comment, 0, eocdComment, 0, comment.length);
    dirty = true;
  }

  /**
   * Sets an extra offset for the central directory. See class description for details. Changing
   * this value will mark the file as dirty and force a rewrite of the central directory when
   * updated.
   *
   * @param offset the offset or {@code 0} to write the central directory at its current location
   * @throws IllegalStateException if file is in read-only mode
   */
  public void setExtraDirectoryOffset(long offset) {
    checkNotInReadOnlyMode();
    Preconditions.checkArgument(offset >= 0, "offset < 0");

    if (extraDirectoryOffset != offset) {
      extraDirectoryOffset = offset;
      deleteDirectoryAndEocd();
      dirty = true;
    }
  }

  /**
   * Obtains the extra offset for the central directory. See class description for details.
   *
   * @return the offset or {@code 0} if no offset is set
   */
  public long getExtraDirectoryOffset() {
    return extraDirectoryOffset;
  }

  /**
   * Obtains whether this {@code ZFile} is ignoring timestamps.
   *
   * @return are the timestamps being ignored?
   */
  public boolean areTimestampsIgnored() {
    return noTimestamps;
  }

  /**
   * Sorts all files in the zip. This will force all files to be loaded and will wait for all
   * background tasks to complete. Sorting files is never done implicitly and will operate in memory
   * only (maybe reading files from the zip disk into memory, if needed). It will leave the zip in
   * dirty state, requiring a call to {@link #update()} to force the entries to be written to disk.
   *
   * @throws IOException failed to load or move a file in the zip
   * @throws IllegalStateException if file is in read-only mode
   */
  public void sortZipContents() throws IOException {
    checkNotInReadOnlyMode();
    reopenRw();

    processAllReadyEntriesWithWait();

    Verify.verify(uncompressedEntries.isEmpty());

    SortedSet<StoredEntry> sortedEntries = Sets.newTreeSet(StoredEntry.COMPARE_BY_NAME);
    for (FileUseMapEntry<StoredEntry> fmEntry : entries.values()) {
      StoredEntry entry = fmEntry.getStore();
      Preconditions.checkNotNull(entry);
      sortedEntries.add(entry);
      entry.loadSourceIntoMemory();

      map.remove(fmEntry);
    }

    entries.clear();
    for (StoredEntry entry : sortedEntries) {
      String name = entry.getCentralDirectoryHeader().getName();
      FileUseMapEntry<StoredEntry> positioned = positionInFile(entry, PositionHint.LOWEST_OFFSET);

      entries.put(name, positioned);
    }

    dirty = true;
  }

  /**
   * Obtains the filesystem path to the zip file.
   *
   * @return the file that may or may not exist (depending on whether something existed there before
   *     the zip was created and on whether the zip has been updated or not)
   */
  public File getFile() {
    return file;
  }

  public DataSource asDataSource() throws IOException {
    if (raf == null) {
      reopenRw();
      Preconditions.checkNotNull(raf, "raf == null");
    }
    return DataSources.asDataSource(this.raf);
  }

  public DataSource asDataSource(long offset, long size) throws IOException {
    if (raf == null) {
      reopenRw();
      Preconditions.checkNotNull(raf, "raf == null");
    }
    return DataSources.asDataSource(this.raf, offset, size);
  }

  /**
   * Creates a new verify log.
   *
   * @return the new verify log
   */
  VerifyLog makeVerifyLog() {
    VerifyLog log = verifyLogFactory.get();
    Preconditions.checkNotNull(log, "log == null");
    return log;
  }

  /**
   * Obtains the zip file's verify log.
   *
   * @return the verify log
   */
  VerifyLog getVerifyLog() {
    return verifyLog;
  }

  /**
   * Are there in-memory changes that have not been written to the zip file?
   *
   * <p>Waits for all pending processing which may make changes.
   */
  public boolean hasPendingChangesWithWait() throws IOException {
    processAllReadyEntriesWithWait();
    return dirty;
  }

  /**
   * Obtains the storage used by the zip to store data.
   *
   * @return the storage object that should only be used to query data; using this storage for any
   *     purposes other than statistics may have undefined results
   */
  public ByteStorage getStorage() {
    return storage;
  }

  /** Hint to where files should be positioned. */
  enum PositionHint {
    /** File may be positioned anywhere, caller doesn't care. */
    ANYWHERE,

    /** File should be positioned at the lowest offset possible. */
    LOWEST_OFFSET
  }
}
