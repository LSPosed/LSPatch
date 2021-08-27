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

package com.android.tools.build.apkzlib.sign;

import java.security.NoSuchAlgorithmException;

/** Signature algorithm. */
public enum SignatureAlgorithm {
  /** RSA algorithm. */
  RSA("RSA", 1, "withRSA"),

  /** ECDSA algorithm. */
  ECDSA("EC", 18, "withECDSA"),

  /** DSA algorithm. */
  DSA("DSA", 1, "withDSA");

  /** Name of the private key as reported by {@code PrivateKey}. */
  public final String keyAlgorithm;

  /** Minimum SDK version that allows this signature. */
  public final int minSdkVersion;

  /** Suffix appended to digest algorithm to obtain signature algorithm. */
  public final String signatureAlgorithmSuffix;

  /**
   * Creates a new signature algorithm.
   *
   * @param keyAlgorithm the name as reported by {@code PrivateKey}
   * @param minSdkVersion minimum SDK version that allows this signature
   * @param signatureAlgorithmSuffix suffix for signature name with used with a digest
   */
  SignatureAlgorithm(String keyAlgorithm, int minSdkVersion, String signatureAlgorithmSuffix) {
    this.keyAlgorithm = keyAlgorithm;
    this.minSdkVersion = minSdkVersion;
    this.signatureAlgorithmSuffix = signatureAlgorithmSuffix;
  }

  /**
   * Obtains the signature algorithm that corresponds to a private key name applicable to a SDK
   * version.
   *
   * @param keyAlgorithm the named referred in the {@code PrivateKey}
   * @param minSdkVersion minimum SDK version to run
   * @return the algorithm that has {@link #keyAlgorithm} equal to {@code keyAlgorithm}
   * @throws NoSuchAlgorithmException if no algorithm was found for the given private key; an
   *     algorithm was found but is not applicable to the given SDK version
   */
  public static SignatureAlgorithm fromKeyAlgorithm(String keyAlgorithm, int minSdkVersion)
      throws NoSuchAlgorithmException {
    for (SignatureAlgorithm alg : values()) {
      if (alg.keyAlgorithm.equalsIgnoreCase(keyAlgorithm)) {
        if (alg.minSdkVersion > minSdkVersion) {
          throw new NoSuchAlgorithmException(
              "Signatures with "
                  + keyAlgorithm
                  + " keys are not supported on minSdkVersion "
                  + minSdkVersion
                  + ". They are supported only for minSdkVersion >= "
                  + alg.minSdkVersion);
        }

        return alg;
      }
    }

    throw new NoSuchAlgorithmException("Signing with " + keyAlgorithm + " keys is not supported");
  }

  /**
   * Obtains the name of the signature algorithm when used with a digest algorithm.
   *
   * @param digestAlgorithm the digest algorithm to use
   * @return the name of the signature algorithm
   */
  public String signatureAlgorithmName(DigestAlgorithm digestAlgorithm) {
    return digestAlgorithm.messageDigestName.replace("-", "") + signatureAlgorithmSuffix;
  }
}
