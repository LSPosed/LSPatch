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

package com.android.apksig.internal.apk.v3;

import static com.android.apksig.internal.apk.ApkSigningBlockUtils.getLengthPrefixedSlice;
import static com.android.apksig.internal.apk.ApkSigningBlockUtils.readLengthPrefixedByteArray;

import com.android.apksig.ApkVerifier.Issue;
import com.android.apksig.SigningCertificateLineage;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.internal.apk.ApkSigningBlockUtils.SignatureNotFoundException;
import com.android.apksig.internal.apk.ContentDigestAlgorithm;
import com.android.apksig.internal.apk.SignatureAlgorithm;
import com.android.apksig.internal.apk.SignatureInfo;
import com.android.apksig.internal.util.AndroidSdkVersion;
import com.android.apksig.internal.util.ByteBufferUtils;
import com.android.apksig.internal.util.X509CertificateUtils;
import com.android.apksig.internal.util.GuaranteedEncodedFormX509Certificate;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.RunnablesExecutor;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * APK Signature Scheme v3 verifier.
 *
 * <p>APK Signature Scheme v3, like v2 is a whole-file signature scheme which aims to protect every
 * single bit of the APK, as opposed to the JAR Signature Scheme which protects only the names and
 * uncompressed contents of ZIP entries.
 *
 * @see <a href="https://source.android.com/security/apksigning/v2.html">APK Signature Scheme v2</a>
 */
public abstract class V3SchemeVerifier {

    private static final int APK_SIGNATURE_SCHEME_V3_BLOCK_ID = 0xf05368c0;

    /** Hidden constructor to prevent instantiation. */
    private V3SchemeVerifier() {}

    /**
     * Verifies the provided APK's APK Signature Scheme v3 signatures and returns the result of
     * verification. The APK must be considered verified only if
     * {@link ApkSigningBlockUtils.Result#verified} is
     * {@code true}. If verification fails, the result will contain errors -- see
     * {@link ApkSigningBlockUtils.Result#getErrors()}.
     *
     * <p>Verification succeeds iff the APK's APK Signature Scheme v3 signatures are expected to
     * verify on all Android platform versions in the {@code [minSdkVersion, maxSdkVersion]} range.
     * If the APK's signature is expected to not verify on any of the specified platform versions,
     * this method returns a result with one or more errors and whose
     * {@code Result.verified == false}, or this method throws an exception.
     *
     * @throws ApkFormatException if the APK is malformed
     * @throws NoSuchAlgorithmException if the APK's signatures cannot be verified because a
     *         required cryptographic algorithm implementation is missing
     * @throws SignatureNotFoundException if no APK Signature Scheme v3
     * signatures are found
     * @throws IOException if an I/O error occurs when reading the APK
     */
    public static ApkSigningBlockUtils.Result verify(
            RunnablesExecutor executor,
            DataSource apk,
            ApkUtils.ZipSections zipSections,
            int minSdkVersion,
            int maxSdkVersion)
            throws IOException, NoSuchAlgorithmException, SignatureNotFoundException {
        ApkSigningBlockUtils.Result result = new ApkSigningBlockUtils.Result(
                ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V3);
        SignatureInfo signatureInfo =
                ApkSigningBlockUtils.findSignature(apk, zipSections,
                        APK_SIGNATURE_SCHEME_V3_BLOCK_ID, result);

        DataSource beforeApkSigningBlock = apk.slice(0, signatureInfo.apkSigningBlockOffset);
        DataSource centralDir =
                apk.slice(
                        signatureInfo.centralDirOffset,
                        signatureInfo.eocdOffset - signatureInfo.centralDirOffset);
        ByteBuffer eocd = signatureInfo.eocd;

        // v3 didn't exist prior to P, so make sure that we're only judging v3 on its supported
        // platforms
        if (minSdkVersion < AndroidSdkVersion.P) {
            minSdkVersion = AndroidSdkVersion.P;
        }

        verify(executor,
                beforeApkSigningBlock,
                signatureInfo.signatureBlock,
                centralDir,
                eocd,
                minSdkVersion,
                maxSdkVersion,
                result);
        return result;
    }

    /**
     * Verifies the provided APK's v3 signatures and outputs the results into the provided
     * {@code result}. APK is considered verified only if there are no errors reported in the
     * {@code result}. See {@link #verify(RunnablesExecutor, DataSource, ApkUtils.ZipSections, int,
     * int)} for more information about the contract of this method.
     *
     * @param result result populated by this method with interesting information about the APK,
     *        such as information about signers, and verification errors and warnings.
     */
    private static void verify(
            RunnablesExecutor executor,
            DataSource beforeApkSigningBlock,
            ByteBuffer apkSignatureSchemeV3Block,
            DataSource centralDir,
            ByteBuffer eocd,
            int minSdkVersion,
            int maxSdkVersion,
            ApkSigningBlockUtils.Result result)
            throws IOException, NoSuchAlgorithmException {
        Set<ContentDigestAlgorithm> contentDigestsToVerify = new HashSet<>(1);
        parseSigners(apkSignatureSchemeV3Block, contentDigestsToVerify, result);

        if (result.containsErrors()) {
            return;
        }
        ApkSigningBlockUtils.verifyIntegrity(
                executor, beforeApkSigningBlock, centralDir, eocd, contentDigestsToVerify, result);

        // make sure that the v3 signers cover the entire targeted sdk version ranges and that the
        // longest SigningCertificateHistory, if present, corresponds to the newest platform
        // versions
        SortedMap<Integer, ApkSigningBlockUtils.Result.SignerInfo> sortedSigners = new TreeMap<>();
        for (ApkSigningBlockUtils.Result.SignerInfo signer : result.signers) {
            sortedSigners.put(signer.minSdkVersion, signer);
        }

        // first make sure there is neither overlap nor holes
        int firstMin = 0;
        int lastMax = 0;
        int lastLineageSize = 0;

        // while we're iterating through the signers, build up the list of lineages
        List<SigningCertificateLineage> lineages = new ArrayList<>(result.signers.size());

        for (ApkSigningBlockUtils.Result.SignerInfo signer : sortedSigners.values()) {
            int currentMin = signer.minSdkVersion;
            int currentMax = signer.maxSdkVersion;
            if (firstMin == 0) {
                // first round sets up our basis
                firstMin = currentMin;
            } else {
                if (currentMin != lastMax + 1) {
                    result.addError(Issue.V3_INCONSISTENT_SDK_VERSIONS);
                    break;
                }
            }
            lastMax = currentMax;

            // also, while we're here, make sure that the lineage sizes only increase
            if (signer.signingCertificateLineage != null) {
                int currLineageSize = signer.signingCertificateLineage.size();
                if (currLineageSize < lastLineageSize) {
                    result.addError(Issue.V3_INCONSISTENT_LINEAGES);
                    break;
                }
                lastLineageSize = currLineageSize;
                lineages.add(signer.signingCertificateLineage);
            }
        }

        // make sure we support our desired sdk ranges
        if (firstMin > minSdkVersion || lastMax < maxSdkVersion) {
            result.addError(Issue.V3_MISSING_SDK_VERSIONS, firstMin, lastMax);
        }

        try {
             result.signingCertificateLineage =
                     SigningCertificateLineage.consolidateLineages(lineages);
        } catch (IllegalArgumentException e) {
            result.addError(Issue.V3_INCONSISTENT_LINEAGES);
        }
        if (!result.containsErrors()) {
            result.verified = true;
        }
    }

    /**
     * Parses each signer in the provided APK Signature Scheme v3 block and populates corresponding
     * {@code signerInfos} of the provided {@code result}.
     *
     * <p>This verifies signatures over {@code signed-data} block contained in each signer block.
     * However, this does not verify the integrity of the rest of the APK but rather simply reports
     * the expected digests of the rest of the APK (see {@code contentDigestsToVerify}).
     *
     * <p>This method adds one or more errors to the {@code result} if a verification error is
     * expected to be encountered on an Android platform version in the
     * {@code [minSdkVersion, maxSdkVersion]} range.
     */
    private static void parseSigners(
            ByteBuffer apkSignatureSchemeV3Block,
            Set<ContentDigestAlgorithm> contentDigestsToVerify,
            ApkSigningBlockUtils.Result result) throws NoSuchAlgorithmException {
        ByteBuffer signers;
        try {
            signers = getLengthPrefixedSlice(apkSignatureSchemeV3Block);
        } catch (ApkFormatException e) {
            result.addError(Issue.V3_SIG_MALFORMED_SIGNERS);
            return;
        }
        if (!signers.hasRemaining()) {
            result.addError(Issue.V3_SIG_NO_SIGNERS);
            return;
        }

        CertificateFactory certFactory;
        try {
            certFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Failed to obtain X.509 CertificateFactory", e);
        }
        int signerCount = 0;
        while (signers.hasRemaining()) {
            int signerIndex = signerCount;
            signerCount++;
            ApkSigningBlockUtils.Result.SignerInfo signerInfo =
                    new ApkSigningBlockUtils.Result.SignerInfo();
            signerInfo.index = signerIndex;
            result.signers.add(signerInfo);
            try {
                ByteBuffer signer = getLengthPrefixedSlice(signers);
                parseSigner(signer, certFactory, signerInfo, contentDigestsToVerify);
            } catch (ApkFormatException | BufferUnderflowException e) {
                signerInfo.addError(Issue.V3_SIG_MALFORMED_SIGNER);
                return;
            }
        }
    }

    /**
     * Parses the provided signer block and populates the {@code result}.
     *
     * <p>This verifies signatures over {@code signed-data} contained in this block, as well as
     * the data contained therein, but does not verify the integrity of the rest of the APK. To
     * facilitate APK integrity verification, this method adds the {@code contentDigestsToVerify}.
     * These digests can then be used to verify the integrity of the APK.
     *
     * <p>This method adds one or more errors to the {@code result} if a verification error is
     * expected to be encountered on an Android platform version in the
     * {@code [minSdkVersion, maxSdkVersion]} range.
     */
    private static void parseSigner(
            ByteBuffer signerBlock,
            CertificateFactory certFactory,
            ApkSigningBlockUtils.Result.SignerInfo result,
            Set<ContentDigestAlgorithm> contentDigestsToVerify)
                    throws ApkFormatException, NoSuchAlgorithmException {
        ByteBuffer signedData = getLengthPrefixedSlice(signerBlock);
        byte[] signedDataBytes = new byte[signedData.remaining()];
        signedData.get(signedDataBytes);
        signedData.flip();
        result.signedData = signedDataBytes;

        int parsedMinSdkVersion = signerBlock.getInt();
        int parsedMaxSdkVersion = signerBlock.getInt();
        result.minSdkVersion = parsedMinSdkVersion;
        result.maxSdkVersion = parsedMaxSdkVersion;
        if (parsedMinSdkVersion < 0 || parsedMinSdkVersion > parsedMaxSdkVersion) {
            result.addError(
                    Issue.V3_SIG_INVALID_SDK_VERSIONS, parsedMinSdkVersion, parsedMaxSdkVersion);
        }
        ByteBuffer signatures = getLengthPrefixedSlice(signerBlock);
        byte[] publicKeyBytes = readLengthPrefixedByteArray(signerBlock);

        // Parse the signatures block and identify supported signatures
        int signatureCount = 0;
        List<ApkSigningBlockUtils.SupportedSignature> supportedSignatures = new ArrayList<>(1);
        while (signatures.hasRemaining()) {
            signatureCount++;
            try {
                ByteBuffer signature = getLengthPrefixedSlice(signatures);
                int sigAlgorithmId = signature.getInt();
                byte[] sigBytes = readLengthPrefixedByteArray(signature);
                result.signatures.add(
                        new ApkSigningBlockUtils.Result.SignerInfo.Signature(
                                sigAlgorithmId, sigBytes));
                SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.findById(sigAlgorithmId);
                if (signatureAlgorithm == null) {
                    result.addWarning(Issue.V3_SIG_UNKNOWN_SIG_ALGORITHM, sigAlgorithmId);
                    continue;
                }
                // TODO consider dropping deprecated signatures for v3 or modifying
                // getSignaturesToVerify (called below)
                supportedSignatures.add(
                        new ApkSigningBlockUtils.SupportedSignature(signatureAlgorithm, sigBytes));
            } catch (ApkFormatException | BufferUnderflowException e) {
                result.addError(Issue.V3_SIG_MALFORMED_SIGNATURE, signatureCount);
                return;
            }
        }
        if (result.signatures.isEmpty()) {
            result.addError(Issue.V3_SIG_NO_SIGNATURES);
            return;
        }

        // Verify signatures over signed-data block using the public key
        List<ApkSigningBlockUtils.SupportedSignature> signaturesToVerify = null;
        try {
            signaturesToVerify =
                    ApkSigningBlockUtils.getSignaturesToVerify(
                            supportedSignatures, result.minSdkVersion, result.maxSdkVersion);
        } catch (ApkSigningBlockUtils.NoSupportedSignaturesException e) {
            result.addError(Issue.V3_SIG_NO_SUPPORTED_SIGNATURES);
            return;
        }
        for (ApkSigningBlockUtils.SupportedSignature signature : signaturesToVerify) {
            SignatureAlgorithm signatureAlgorithm = signature.algorithm;
            String jcaSignatureAlgorithm =
                    signatureAlgorithm.getJcaSignatureAlgorithmAndParams().getFirst();
            AlgorithmParameterSpec jcaSignatureAlgorithmParams =
                    signatureAlgorithm.getJcaSignatureAlgorithmAndParams().getSecond();
            String keyAlgorithm = signatureAlgorithm.getJcaKeyAlgorithm();
            PublicKey publicKey;
            try {
                publicKey =
                        KeyFactory.getInstance(keyAlgorithm).generatePublic(
                                new X509EncodedKeySpec(publicKeyBytes));
            } catch (Exception e) {
                result.addError(Issue.V3_SIG_MALFORMED_PUBLIC_KEY, e);
                return;
            }
            try {
                Signature sig = Signature.getInstance(jcaSignatureAlgorithm);
                sig.initVerify(publicKey);
                if (jcaSignatureAlgorithmParams != null) {
                    sig.setParameter(jcaSignatureAlgorithmParams);
                }
                signedData.position(0);
                sig.update(signedData);
                byte[] sigBytes = signature.signature;
                if (!sig.verify(sigBytes)) {
                    result.addError(Issue.V3_SIG_DID_NOT_VERIFY, signatureAlgorithm);
                    return;
                }
                result.verifiedSignatures.put(signatureAlgorithm, sigBytes);
                contentDigestsToVerify.add(signatureAlgorithm.getContentDigestAlgorithm());
            } catch (InvalidKeyException | InvalidAlgorithmParameterException
                    | SignatureException e) {
                result.addError(Issue.V3_SIG_VERIFY_EXCEPTION, signatureAlgorithm, e);
                return;
            }
        }

        // At least one signature over signedData has verified. We can now parse signed-data.
        signedData.position(0);
        ByteBuffer digests = getLengthPrefixedSlice(signedData);
        ByteBuffer certificates = getLengthPrefixedSlice(signedData);

        int signedMinSdkVersion = signedData.getInt();
        if (signedMinSdkVersion != parsedMinSdkVersion) {
            result.addError(
                    Issue.V3_MIN_SDK_VERSION_MISMATCH_BETWEEN_SIGNER_AND_SIGNED_DATA_RECORD,
                    parsedMinSdkVersion,
                    signedMinSdkVersion);
        }
        int signedMaxSdkVersion = signedData.getInt();
        if (signedMaxSdkVersion != parsedMaxSdkVersion) {
            result.addError(
                    Issue.V3_MAX_SDK_VERSION_MISMATCH_BETWEEN_SIGNER_AND_SIGNED_DATA_RECORD,
                    parsedMaxSdkVersion,
                    signedMaxSdkVersion);
        }
        ByteBuffer additionalAttributes = getLengthPrefixedSlice(signedData);

        // Parse the certificates block
        int certificateIndex = -1;
        while (certificates.hasRemaining()) {
            certificateIndex++;
            byte[] encodedCert = readLengthPrefixedByteArray(certificates);
            X509Certificate certificate;
            try {
                certificate = X509CertificateUtils.generateCertificate(encodedCert, certFactory);
            } catch (CertificateException e) {
                result.addError(
                        Issue.V3_SIG_MALFORMED_CERTIFICATE,
                        certificateIndex,
                        certificateIndex + 1,
                        e);
                return;
            }
            // Wrap the cert so that the result's getEncoded returns exactly the original encoded
            // form. Without this, getEncoded may return a different form from what was stored in
            // the signature. This is because some X509Certificate(Factory) implementations
            // re-encode certificates.
            certificate = new GuaranteedEncodedFormX509Certificate(certificate, encodedCert);
            result.certs.add(certificate);
        }

        if (result.certs.isEmpty()) {
            result.addError(Issue.V3_SIG_NO_CERTIFICATES);
            return;
        }
        X509Certificate mainCertificate = result.certs.get(0);
        byte[] certificatePublicKeyBytes;
        try {
            certificatePublicKeyBytes = ApkSigningBlockUtils.encodePublicKey(mainCertificate.getPublicKey());
        } catch (InvalidKeyException e) {
            System.out.println("Caught an exception encoding the public key: " + e);
            e.printStackTrace();
            certificatePublicKeyBytes = mainCertificate.getPublicKey().getEncoded();
        }
        if (!Arrays.equals(publicKeyBytes, certificatePublicKeyBytes)) {
            result.addError(
                    Issue.V3_SIG_PUBLIC_KEY_MISMATCH_BETWEEN_CERTIFICATE_AND_SIGNATURES_RECORD,
                    ApkSigningBlockUtils.toHex(certificatePublicKeyBytes),
                    ApkSigningBlockUtils.toHex(publicKeyBytes));
            return;
        }

        // Parse the digests block
        int digestCount = 0;
        while (digests.hasRemaining()) {
            digestCount++;
            try {
                ByteBuffer digest = getLengthPrefixedSlice(digests);
                int sigAlgorithmId = digest.getInt();
                byte[] digestBytes = readLengthPrefixedByteArray(digest);
                result.contentDigests.add(
                        new ApkSigningBlockUtils.Result.SignerInfo.ContentDigest(
                                sigAlgorithmId, digestBytes));
            } catch (ApkFormatException | BufferUnderflowException e) {
                result.addError(Issue.V3_SIG_MALFORMED_DIGEST, digestCount);
                return;
            }
        }

        List<Integer> sigAlgsFromSignaturesRecord = new ArrayList<>(result.signatures.size());
        for (ApkSigningBlockUtils.Result.SignerInfo.Signature signature : result.signatures) {
            sigAlgsFromSignaturesRecord.add(signature.getAlgorithmId());
        }
        List<Integer> sigAlgsFromDigestsRecord = new ArrayList<>(result.contentDigests.size());
        for (ApkSigningBlockUtils.Result.SignerInfo.ContentDigest digest : result.contentDigests) {
            sigAlgsFromDigestsRecord.add(digest.getSignatureAlgorithmId());
        }

        if (!sigAlgsFromSignaturesRecord.equals(sigAlgsFromDigestsRecord)) {
            result.addError(
                    Issue.V3_SIG_SIG_ALG_MISMATCH_BETWEEN_SIGNATURES_AND_DIGESTS_RECORDS,
                    sigAlgsFromSignaturesRecord,
                    sigAlgsFromDigestsRecord);
            return;
        }

        // Parse the additional attributes block.
        int additionalAttributeCount = 0;
        while (additionalAttributes.hasRemaining()) {
            additionalAttributeCount++;
            try {
                ByteBuffer attribute =
                        getLengthPrefixedSlice(additionalAttributes);
                int id = attribute.getInt();
                byte[] value = ByteBufferUtils.toByteArray(attribute);
                result.additionalAttributes.add(
                        new ApkSigningBlockUtils.Result.SignerInfo.AdditionalAttribute(id, value));
                if (id == V3SchemeSigner.PROOF_OF_ROTATION_ATTR_ID) {
                    try {
                        // SigningCertificateLineage is verified when built
                        result.signingCertificateLineage =
                                SigningCertificateLineage.readFromV3AttributeValue(value);
                        // make sure that the last cert in the chain matches this signer cert
                        SigningCertificateLineage subLineage =
                                result.signingCertificateLineage.getSubLineage(result.certs.get(0));
                        if (result.signingCertificateLineage.size() != subLineage.size()) {
                            result.addError(Issue.V3_SIG_POR_CERT_MISMATCH);
                        }
                    } catch (SecurityException e) {
                        result.addError(Issue.V3_SIG_POR_DID_NOT_VERIFY);
                    } catch (IllegalArgumentException e) {
                        result.addError(Issue.V3_SIG_POR_CERT_MISMATCH);
                    } catch (Exception e) {
                        result.addError(Issue.V3_SIG_MALFORMED_LINEAGE);
                    }
                } else {
                    result.addWarning(Issue.V3_SIG_UNKNOWN_ADDITIONAL_ATTRIBUTE, id);
                }
            } catch (ApkFormatException | BufferUnderflowException e) {
                result.addError(
                        Issue.V3_SIG_MALFORMED_ADDITIONAL_ATTRIBUTE, additionalAttributeCount);
                return;
            }
        }
    }

}
