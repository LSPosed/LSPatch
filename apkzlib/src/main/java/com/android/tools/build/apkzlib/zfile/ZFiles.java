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

import com.android.tools.build.apkzlib.sign.ManifestGenerationExtension;
import com.android.tools.build.apkzlib.sign.SigningExtension;
import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.zip.AlignmentRule;
import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.google.common.base.Optional;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.annotation.Nullable;

/** Factory for {@link ZFile}s that are specifically configured to be APKs, AARs, ... */
public class ZFiles {

  /** By default all non-compressed files are alignment at 4 byte boundaries.. */
  private static final AlignmentRule APK_DEFAULT_RULE = AlignmentRules.constant(4);

  /** Default build by string. */
  private static final String DEFAULT_BUILD_BY = "Generated-by-ADT";

  /** Default created by string. */
  private static final String DEFAULT_CREATED_BY = "Generated-by-ADT";

  /**
   * Creates a new zip file configured as an apk, based on a given file.
   *
   * @param f the file, if this path does not represent an existing path, will create a {@link
   *     ZFile} based on an non-existing path (a zip will be created when {@link ZFile#close()} is
   *     invoked)
   * @param options the options to create the {@link ZFile}
   * @return the zip file
   * @throws IOException failed to create the zip file
   */
  public static ZFile apk(File f, ZFileOptions options) throws IOException {
    options.setAlignmentRule(AlignmentRules.compose(options.getAlignmentRule(), APK_DEFAULT_RULE));
    return ZFile.openReadWrite(f, options);
  }

  /**
   * Creates a new zip file configured as an apk, based on a given file.
   *
   * @param f the file, if this path does not represent an existing path, will create a {@link
   *     ZFile} based on an non-existing path (a zip will be created when {@link ZFile#close()} is
   *     invoked)
   * @param options the options to create the {@link ZFile}
   * @param signingOptions the options to sign the apk
   * @param builtBy who to mark as builder in the manifest
   * @param createdBy who to mark as creator in the manifest
   * @return the zip file
   * @throws IOException failed to create the zip file
   */
  public static ZFile apk(
      File f,
      ZFileOptions options,
      Optional<SigningOptions> signingOptions,
      @Nullable String builtBy,
      @Nullable String createdBy)
      throws IOException {
    return apk(
        f, options, signingOptions, builtBy, createdBy, options.getAlwaysGenerateJarManifest());
  }

  /**
   * Creates a new zip file configured as an apk, based on a given file.
   *
   * @param f the file, if this path does not represent an existing path, will create a {@link
   *     ZFile} based on an non-existing path (a zip will be created when {@link ZFile#close()} is
   *     invoked)
   * @param options the options to create the {@link ZFile}
   * @param signingOptions the options to sign the apk
   * @param builtBy who to mark as builder in the manifest
   * @param createdBy who to mark as creator in the manifest
   * @param writeManifest a migration parameter that forces keeping (useless) manifest.mf file in
   *     apk file in order to prevent breaking changes. Clients of the previous interface will still
   *     get apk with manifest.mf because the flag is true by default
   * @return the zip file
   * @throws IOException failed to create the zip file
   * @deprecated Use ZFileOptions.setAlwaysGenerateJarManifest() instead.
   */
  @Deprecated
  // This method can be removed once ZFileOptions.getAlwaysGenerateJarManifest() is on Maven.
  public static ZFile apk(
      File f,
      ZFileOptions options,
      Optional<SigningOptions> signingOptions,
      @Nullable String builtBy,
      @Nullable String createdBy,
      boolean writeManifest)
      throws IOException {
    ZFile zfile = apk(f, options);

    if ((signingOptions.isPresent() && signingOptions.get().isV1SigningEnabled())
        || writeManifest) {
      if (builtBy == null) {
        builtBy = DEFAULT_BUILD_BY;
      }

      if (createdBy == null) {
        createdBy = DEFAULT_CREATED_BY;
      }
      ManifestGenerationExtension manifestExt = new ManifestGenerationExtension(builtBy, createdBy);
      manifestExt.register(zfile);
    }

    if (signingOptions.isPresent()) {
      SigningOptions signOptions = signingOptions.get();
      try {
        new SigningExtension(signOptions).register(zfile);
      } catch (NoSuchAlgorithmException | InvalidKeyException e) {
        throw new IOException("Failed to create signature extensions", e);
      }
    }

    return zfile;
  }

  private ZFiles() {}
}
