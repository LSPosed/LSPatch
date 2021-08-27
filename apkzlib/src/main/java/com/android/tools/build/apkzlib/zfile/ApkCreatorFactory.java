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

import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import java.io.File;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Factory that creates instances of {@link ApkCreator}. */
public interface ApkCreatorFactory {

  /**
   * Creates an {@link ApkCreator} with a given output location, and signing information.
   *
   * @param creationData the information to create the APK
   */
  ApkCreator make(CreationData creationData);

  /**
   * Data structure with the required information to initiate the creation of an APK. See {@link
   * ApkCreatorFactory#make(CreationData)}.
   */
  @AutoValue
  abstract class CreationData {

    /** An implementation of builder pattern to create a {@link CreationData} object. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setApkPath(@Nonnull File apkPath);

      public abstract Builder setSigningOptions(@Nonnull SigningOptions signingOptions);

      public abstract Builder setBuiltBy(@Nullable String buildBy);

      public abstract Builder setCreatedBy(@Nullable String createdBy);

      public abstract Builder setNativeLibrariesPackagingMode(
          NativeLibrariesPackagingMode packagingMode);

      public abstract Builder setNoCompressPredicate(Predicate<String> predicate);

      public abstract Builder setIncremental(boolean incremental);

      abstract CreationData autoBuild();

      public CreationData build() {
        CreationData data = autoBuild();
        Preconditions.checkArgument(data.getApkPath() != null, "Output apk path is not set");
        return data;
      }
    }

    public static Builder builder() {
      return new AutoValue_ApkCreatorFactory_CreationData.Builder()
          .setBuiltBy(null)
          .setCreatedBy(null)
          .setNoCompressPredicate(s -> false)
          .setIncremental(false);
    }

    /**
     * Obtains the path where the APK should be located. If the path already exists, then the APK
     * may be updated instead of re-created.
     *
     * @return the path that may already exist or not
     */
    public abstract File getApkPath();

    /**
     * Obtains the data used to sign the APK.
     *
     * @return the SigningOptions
     */
    @Nonnull
    public abstract Optional<SigningOptions> getSigningOptions();

    /**
     * Obtains the "built-by" text for the APK.
     *
     * @return the text or {@code null} if the default should be used
     */
    @Nullable
    public abstract String getBuiltBy();

    /**
     * Obtains the "created-by" text for the APK.
     *
     * @return the text or {@code null} if the default should be used
     */
    @Nullable
    public abstract String getCreatedBy();

    /** Returns the packaging policy that the {@link ApkCreator} should use for native libraries. */
    public abstract NativeLibrariesPackagingMode getNativeLibrariesPackagingMode();

    /** Returns the predicate to decide which file paths should be uncompressed. */
    public abstract Predicate<String> getNoCompressPredicate();

    /**
     * Returns if this apk build is incremental.
     *
     * As mentioned in {@link getApkPath} description, we may already have an existing apk in place.
     * This is the case when e.g. building APK via build system and this is not the first build.
     * In that case the build is called incremental and internal APK data might be reused speeding
     * the build up.
     */
    public abstract boolean isIncremental();
  }
}
