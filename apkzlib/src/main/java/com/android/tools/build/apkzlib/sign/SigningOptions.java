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

package com.android.tools.build.apkzlib.sign;

import com.android.apksig.util.RunnablesExecutor;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A class that contains data to initialize SigningExtension. */
@AutoValue
public abstract class SigningOptions {

    /** An implementation of builder pattern to create a {@link SigningOptions} object. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setKey(@Nonnull PrivateKey key);
        public abstract Builder setCertificates(@Nonnull ImmutableList<X509Certificate> certs);
        public abstract Builder setCertificates(X509Certificate... certs);
        public abstract Builder setV1SigningEnabled(boolean enabled);
        public abstract Builder setV2SigningEnabled(boolean enabled);
        public abstract Builder setMinSdkVersion(int version);
        public abstract Builder setValidation(@Nonnull Validation validation);
        public abstract Builder setExecutor(@Nullable RunnablesExecutor executor);
        public abstract Builder setSdkDependencyData(@Nullable byte[] sdkDependencyData);

        abstract SigningOptions autoBuild();

        public SigningOptions build() {
            SigningOptions options = autoBuild();
            Preconditions.checkArgument(options.getMinSdkVersion() >= 0, "minSdkVersion < 0");
            Preconditions.checkArgument(
                    !options.getCertificates().isEmpty(),
                    "There should be at least one certificate in SigningOptions");
            return options;
        }
    }

    public static Builder builder() {
        return new AutoValue_SigningOptions.Builder()
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(false)
                .setValidation(Validation.ALWAYS_VALIDATE);
    }

    /** {@link PrivateKey} used to sign the archive. */
    public abstract PrivateKey getKey();

    /**
     * A list of the {@link X509Certificate}s to embed in the signed APKs. The first
     * element of the list must be the certificate associated with the private key.
     */
    public abstract ImmutableList<X509Certificate> getCertificates();

    /** Shows whether signing with JAR Signature Scheme (aka v1 signing) is enabled. */
    public abstract boolean isV1SigningEnabled();

    /** Shows whether signing with APK Signature Scheme v2 (aka v2 signing) is enabled. */
    public abstract boolean isV2SigningEnabled();

    /** Minimum SDK version supported. */
    public abstract int getMinSdkVersion();

    /** Strategy of package signature validation */
    public abstract Validation getValidation();

    @Nullable
    public abstract RunnablesExecutor getExecutor();

  /** SDK dependencies of the APK */
  @SuppressWarnings("mutable")
  @Nullable
  public abstract byte[] getSdkDependencyData();

    public enum Validation {
        /** Always perform signature validation */
        ALWAYS_VALIDATE,
        /**
         * Assume the signature is valid without validation i.e. don't resign if no files changed
         */
        ASSUME_VALID,
        /** Assume the signature is invalid without validation i.e. unconditionally resign */
        ASSUME_INVALID,
    }
}
