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

import com.android.tools.build.apkzlib.bytestorage.ByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.ChunkBasedByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.OverflowToDiskByteStorageFactory;
import com.android.tools.build.apkzlib.bytestorage.TemporaryDirectory;
import com.android.tools.build.apkzlib.zip.compress.DeflateExecutionCompressor;
import com.android.tools.build.apkzlib.zip.utils.ByteTracker;
import com.google.common.base.Supplier;
import java.util.zip.Deflater;

/** Options to create a {@link ZFile}. */
public class ZFileOptions {

  /** The storage to use. */
  private ByteStorageFactory storageFactory;

  /** The compressor to use. */
  private Compressor compressor;

  /** Should timestamps be zeroed? */
  private boolean noTimestamps;

  /** The alignment rule to use. */
  private AlignmentRule alignmentRule;

  /** Should the extra field be used to cover empty space? */
  private boolean coverEmptySpaceUsingExtraField;

  /** Should files be automatically sorted before update? */
  private boolean autoSortFiles;

  /**
   * Skip expensive validation during {@link ZFile} creation?
   *
   * <p>During incremental build we are absolutely sure that the zip file is valid, so we do not
   * have to spend time verifying different fields (some of these checks are relatively expensive
   * and should be skipped if possible for performance)
   */
  private boolean skipValidation;

  /** Factory creating verification logs to use. */
  private Supplier<VerifyLog> verifyLogFactory;

  /**
   * Whether to always generate the MANIFEST.MF file regardless whether the APK will be signed with
   * v1 signing scheme (i.e. jar signing).
   */
  private boolean alwaysGenerateJarManifest;

  /** Creates a new options object. All options are set to their defaults. */
  public ZFileOptions() {
    storageFactory =
        new ChunkBasedByteStorageFactory(
            new OverflowToDiskByteStorageFactory(TemporaryDirectory::newSystemTemporaryDirectory));
    compressor = new DeflateExecutionCompressor(Runnable::run, Deflater.DEFAULT_COMPRESSION);
    alignmentRule = AlignmentRules.compose();
    verifyLogFactory = VerifyLogs::devNull;

    // We set this to true because many utilities stream the zip and expect no space between entries
    // in the zip file.
    coverEmptySpaceUsingExtraField = true;
    skipValidation = false;
    // True by default for backwards compatibility.
    alwaysGenerateJarManifest = true;
  }

  /**
   * Obtains the ZFile's byte storage factory.
   *
   * @return the factory used to create byte storages used to store data
   */
  public ByteStorageFactory getStorageFactory() {
    return storageFactory;
  }

  @Deprecated
  public ByteTracker getTracker() {
    return new ByteTracker();
  }

  /**
   * Sets the byte storage factory to use.
   *
   * @param storage the factory to use to create storage for new instances of {@link ZFile} created
   *     for these options.
   */
  public ZFileOptions setStorageFactory(ByteStorageFactory storage) {
    this.storageFactory = storage;
    return this;
  }

  /**
   * Obtains the compressor to use.
   *
   * @return the compressor
   */
  public Compressor getCompressor() {
    return compressor;
  }

  /**
   * Sets the compressor to use.
   *
   * @param compressor the compressor
   */
  public ZFileOptions setCompressor(Compressor compressor) {
    this.compressor = compressor;
    return this;
  }

  /**
   * Obtains whether timestamps should be zeroed.
   *
   * @return should timestamps be zeroed?
   */
  public boolean getNoTimestamps() {
    return noTimestamps;
  }

  /**
   * Sets whether timestamps should be zeroed.
   *
   * @param noTimestamps should timestamps be zeroed?
   */
  public ZFileOptions setNoTimestamps(boolean noTimestamps) {
    this.noTimestamps = noTimestamps;
    return this;
  }

  /**
   * Obtains the alignment rule.
   *
   * @return the alignment rule
   */
  public AlignmentRule getAlignmentRule() {
    return alignmentRule;
  }

  /**
   * Sets the alignment rule.
   *
   * @param alignmentRule the alignment rule
   */
  public ZFileOptions setAlignmentRule(AlignmentRule alignmentRule) {
    this.alignmentRule = alignmentRule;
    return this;
  }

  /**
   * Obtains whether the extra field should be used to cover empty spaces. See {@link ZFile} for an
   * explanation on using the extra field for covering empty spaces.
   *
   * @return should the extra field be used to cover empty spaces?
   */
  public boolean getCoverEmptySpaceUsingExtraField() {
    return coverEmptySpaceUsingExtraField;
  }

  /**
   * Sets whether the extra field should be used to cover empty spaces. See {@link ZFile} for an
   * explanation on using the extra field for covering empty spaces.
   *
   * @param coverEmptySpaceUsingExtraField should the extra field be used to cover empty spaces?
   */
  public ZFileOptions setCoverEmptySpaceUsingExtraField(boolean coverEmptySpaceUsingExtraField) {
    this.coverEmptySpaceUsingExtraField = coverEmptySpaceUsingExtraField;
    return this;
  }

  /**
   * Obtains whether files should be automatically sorted before updating the zip file. See {@link
   * ZFile} for an explanation on automatic sorting.
   *
   * @return should the file be automatically sorted?
   */
  public boolean getAutoSortFiles() {
    return autoSortFiles;
  }

  /**
   * Sets whether files should be automatically sorted before updating the zip file. See {@link
   * ZFile} for an explanation on automatic sorting.
   *
   * @param autoSortFiles should the file be automatically sorted?
   */
  public ZFileOptions setAutoSortFiles(boolean autoSortFiles) {
    this.autoSortFiles = autoSortFiles;
    return this;
  }

  /**
   * Sets the verification log factory.
   *
   * @param verifyLogFactory verification log factory
   */
  public ZFileOptions setVerifyLogFactory(Supplier<VerifyLog> verifyLogFactory) {
    this.verifyLogFactory = verifyLogFactory;
    return this;
  }

  /**
   * Obtains the verification log factory. By default, the verification log doesn't store anything
   * and will always return an empty log.
   *
   * @return the verification log factory
   */
  public Supplier<VerifyLog> getVerifyLogFactory() {
    return verifyLogFactory;
  }

  /**
   * Sets whether expensive validation should be skipped during {@link ZFile} creation
   *
   * @param skipValidation during creation?
   */
  public ZFileOptions setSkipValidation(boolean skipValidation) {
    this.skipValidation = skipValidation;
    return this;
  }

  /**
   * Gets whether expensive validation should be performed during {@link ZFile} creation
   *
   * @return skip verification during creation?
   */
  public boolean getSkipValidation() {
    return skipValidation;
  }

  /**
   * Sets whether to always generate the MANIFEST.MF file, regardless whether the APK is signed with
   * v1 signing scheme.
   */
  public ZFileOptions setAlwaysGenerateJarManifest(boolean alwaysGenerateJarManifest) {
    this.alwaysGenerateJarManifest = alwaysGenerateJarManifest;
    return this;
  }

  /** Returns whether the MANIFEST.MF file should always be generated. */
  public boolean getAlwaysGenerateJarManifest() {
    return alwaysGenerateJarManifest;
  }
}
