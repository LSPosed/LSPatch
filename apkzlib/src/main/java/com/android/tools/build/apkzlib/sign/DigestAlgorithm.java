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


/** Message digest algorithms. */
public enum DigestAlgorithm {
  /**
   * SHA-1 digest.
   *
   * <p>Android 2.3 (API Level 9) to 4.2 (API Level 17) (inclusive) do not support SHA-2 JAR
   * signatures.
   *
   * <p>Moreover, platforms prior to API Level 18, without the additional Digest-Algorithms
   * attribute, only support SHA or SHA1 algorithm names in .SF and MANIFEST.MF attributes.
   */
  SHA1("SHA1", "SHA-1"),

  /** SHA-256 digest. */
  SHA256("SHA-256", "SHA-256");

  /**
   * API level which supports {@link #SHA256} with {@link SignatureAlgorithm#RSA} and {@link
   * SignatureAlgorithm#ECDSA}.
   */
  public static final int API_SHA_256_RSA_AND_ECDSA = 18;

  /**
   * API level which supports {@link #SHA256} for all {@link SignatureAlgorithm}s.
   *
   * <p>Before that, SHA256 can only be used with RSA and ECDSA.
   */
  public static final int API_SHA_256_ALL_ALGORITHMS = 21;

  /** Name of algorithm for message digest. */
  public final String messageDigestName;

  /** Name of attribute in signature file with the manifest digest. */
  public final String manifestAttributeName;

  /** Name of attribute in entry (both manifest and signature file) with the entry's digest. */
  public final String entryAttributeName;

  /**
   * Creates a digest algorithm.
   *
   * @param attributeName attribute name in the signature file
   * @param messageDigestName name of algorithm for message digest
   */
  DigestAlgorithm(String attributeName, String messageDigestName) {
    this.messageDigestName = messageDigestName;
    this.entryAttributeName = attributeName + "-Digest";
    this.manifestAttributeName = attributeName + "-Digest-Manifest";
  }
}
