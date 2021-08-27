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

package com.android.tools.build.apkzlib.zfile;

import com.android.tools.build.apkzlib.zip.AlignmentRule;
import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.io.Closer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

/** {@link ApkCreator} that uses {@link ZFileOptions} to generate the APK. */
class ApkZFileCreator implements ApkCreator {

  /** Suffix for native libraries. */
  private static final String NATIVE_LIBRARIES_SUFFIX = ".so";

  /** Shared libraries are alignment at 4096 boundaries. */
  private static final AlignmentRule SO_RULE =
      AlignmentRules.constantForSuffix(NATIVE_LIBRARIES_SUFFIX, 4096);

  /** The zip file. */
  private final ZFile zip;

  /** Has the zip file been closed? */
  private boolean closed;

  /** Predicate defining which files should not be compressed. */
  private final Predicate<String> noCompressPredicate;

  /**
   * Creates a new creator.
   *
   * @param creationData the data needed to create the APK
   * @param options zip file options
   * @throws IOException failed to create the zip
   */
  ApkZFileCreator(ApkCreatorFactory.CreationData creationData, ZFileOptions options)
      throws IOException {

    switch (creationData.getNativeLibrariesPackagingMode()) {
      case COMPRESSED:
        noCompressPredicate = creationData.getNoCompressPredicate();
        break;
      case UNCOMPRESSED_AND_ALIGNED:
        Predicate<String> baseNoCompressPredicate = creationData.getNoCompressPredicate();
        noCompressPredicate =
            name -> baseNoCompressPredicate.apply(name) || name.endsWith(NATIVE_LIBRARIES_SUFFIX);
        options.setAlignmentRule(AlignmentRules.compose(SO_RULE, options.getAlignmentRule()));
        break;
      default:
        throw new AssertionError();
    }
    // In case of incremental build we can skip validation since we generated the previous apk and
    // we trust ourselves
    options.setSkipValidation(creationData.isIncremental());

    zip =
        ZFiles.apk(
            creationData.getApkPath(),
            options,
            creationData.getSigningOptions(),
            creationData.getBuiltBy(),
            creationData.getCreatedBy());
    closed = false;
  }

  @Override
  public void writeZip(
      File zip, @Nullable Function<String, String> transform, @Nullable Predicate<String> isIgnored)
      throws IOException {
    Preconditions.checkState(!closed, "closed == true");
    Preconditions.checkArgument(zip.isFile(), "!zip.isFile()");

    Closer closer = Closer.create();
    try {
      ZFile toMerge = closer.register(ZFile.openReadWrite(zip));

      Predicate<String> ignorePredicate;
      if (isIgnored == null) {
        ignorePredicate = s -> false;
      } else {
        ignorePredicate = isIgnored;
      }

      // Files that *must* be uncompressed in the result should not be merged and should be
      // added after. This is just very slightly less efficient than ignoring just the ones
      // that were compressed and must be uncompressed, but it is a lot simpler :)
      Predicate<String> noMergePredicate =
          v -> ignorePredicate.apply(v) || noCompressPredicate.apply(v);

      this.zip.mergeFrom(toMerge, noMergePredicate);

      for (StoredEntry toMergeEntry : toMerge.entries()) {
        String path = toMergeEntry.getCentralDirectoryHeader().getName();
        if (noCompressPredicate.apply(path) && !ignorePredicate.apply(path)) {
          // This entry *must* be uncompressed so it was ignored in the merge and should
          // now be added to the apk.
          try (InputStream ignoredData = toMergeEntry.open()) {
            this.zip.add(path, ignoredData, false);
          }
        }
      }
    } catch (Throwable t) {
      throw closer.rethrow(t);
    } finally {
      closer.close();
    }
  }

  @Override
  public void writeFile(File inputFile, String apkPath) throws IOException {
    Preconditions.checkState(!closed, "closed == true");

    boolean mayCompress = !noCompressPredicate.apply(apkPath);

    Closer closer = Closer.create();
    try {
      FileInputStream inputFileStream = closer.register(new FileInputStream(inputFile));
      zip.add(apkPath, inputFileStream, mayCompress);
    } catch (IOException e) {
      throw closer.rethrow(e, IOException.class);
    } catch (Throwable t) {
      throw closer.rethrow(t);
    } finally {
      closer.close();
    }
  }

  @Override
  public void deleteFile(String apkPath) throws IOException {
    Preconditions.checkState(!closed, "closed == true");

    StoredEntry entry = zip.get(apkPath);
    if (entry != null) {
      entry.delete();
    }
  }

  @Override
  public boolean hasPendingChangesWithWait() throws IOException {
    return zip.hasPendingChangesWithWait();
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }

    zip.close();
    closed = true;
  }
}
