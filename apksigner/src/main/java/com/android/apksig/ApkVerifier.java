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

package com.android.apksig;

import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.internal.apk.AndroidBinXmlParser;
import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.internal.apk.v1.V1SchemeVerifier;
import com.android.apksig.internal.apk.ContentDigestAlgorithm;
import com.android.apksig.internal.apk.SignatureAlgorithm;
import com.android.apksig.internal.apk.v2.V2SchemeVerifier;
import com.android.apksig.internal.apk.v3.V3SchemeVerifier;
import com.android.apksig.internal.util.AndroidSdkVersion;
import com.android.apksig.internal.zip.CentralDirectoryRecord;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.apksig.util.RunnablesExecutor;
import com.android.apksig.zip.ZipFormatException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * APK signature verifier which mimics the behavior of the Android platform.
 *
 * <p>The verifier is designed to closely mimic the behavior of Android platforms. This is to enable
 * the verifier to be used for checking whether an APK's signatures are expected to verify on
 * Android.
 *
 * <p>Use {@link Builder} to obtain instances of this verifier.
 *
 * @see <a href="https://source.android.com/security/apksigning/index.html">Application Signing</a>
 */
public class ApkVerifier {

    private static final Map<Integer, String> SUPPORTED_APK_SIG_SCHEME_NAMES =
            loadSupportedApkSigSchemeNames();

    private static Map<Integer,String> loadSupportedApkSigSchemeNames() {
        Map<Integer, String> supportedMap = new HashMap<>(2);
        supportedMap.put(
                ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V2, "APK Signature Scheme v2");
        supportedMap.put(
                ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V3, "APK Signature Scheme v3");
        return supportedMap;
    }

    private final File mApkFile;
    private final DataSource mApkDataSource;

    private final Integer mMinSdkVersion;
    private final int mMaxSdkVersion;

    private ApkVerifier(
            File apkFile,
            DataSource apkDataSource,
            Integer minSdkVersion,
            int maxSdkVersion) {
        mApkFile = apkFile;
        mApkDataSource = apkDataSource;
        mMinSdkVersion = minSdkVersion;
        mMaxSdkVersion = maxSdkVersion;
    }

    /**
     * Verifies the APK's signatures and returns the result of verification. The APK can be
     * considered verified iff the result's {@link Result#isVerified()} returns {@code true}.
     * The verification result also includes errors, warnings, and information about signers such
     * as their signing certificates.
     *
     * <p>Verification succeeds iff the APK's signature is expected to verify on all Android
     * platform versions specified via the {@link Builder}. If the APK's signature is expected to
     * not verify on any of the specified platform versions, this method returns a result with one
     * or more errors and whose {@link Result#isVerified()} returns {@code false}, or this method
     * throws an exception.
     *
     * @throws IOException if an I/O error is encountered while reading the APK
     * @throws ApkFormatException if the APK is malformed
     * @throws NoSuchAlgorithmException if the APK's signatures cannot be verified because a
     *         required cryptographic algorithm implementation is missing
     * @throws IllegalStateException if this verifier's configuration is missing required
     *         information.
     */
    public Result verify() throws IOException, ApkFormatException, NoSuchAlgorithmException,
            IllegalStateException {
        Closeable in = null;
        try {
            DataSource apk;
            if (mApkDataSource != null) {
                apk = mApkDataSource;
            } else if (mApkFile != null) {
                RandomAccessFile f = new RandomAccessFile(mApkFile, "r");
                in = f;
                apk = DataSources.asDataSource(f, 0, f.length());
            } else {
                throw new IllegalStateException("APK not provided");
            }
            return verify(apk);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Verifies the APK's signatures and returns the result of verification. The APK can be
     * considered verified iff the result's {@link Result#isVerified()} returns {@code true}.
     * The verification result also includes errors, warnings, and information about signers.
     *
     * @param apk APK file contents
     *
     * @throws IOException if an I/O error is encountered while reading the APK
     * @throws ApkFormatException if the APK is malformed
     * @throws NoSuchAlgorithmException if the APK's signatures cannot be verified because a
     *         required cryptographic algorithm implementation is missing
     */
    private Result verify(DataSource apk)
            throws IOException, ApkFormatException, NoSuchAlgorithmException {
        if (mMinSdkVersion != null) {
            if (mMinSdkVersion < 0) {
                throw new IllegalArgumentException(
                        "minSdkVersion must not be negative: " + mMinSdkVersion);
            }
            if ((mMinSdkVersion != null) && (mMinSdkVersion > mMaxSdkVersion)) {
                throw new IllegalArgumentException(
                        "minSdkVersion (" + mMinSdkVersion + ") > maxSdkVersion (" + mMaxSdkVersion
                                + ")");
            }
        }
        int maxSdkVersion = mMaxSdkVersion;

        ApkUtils.ZipSections zipSections;
        try {
            zipSections = ApkUtils.findZipSections(apk);
        } catch (ZipFormatException e) {
            throw new ApkFormatException("Malformed APK: not a ZIP archive", e);
        }

        ByteBuffer androidManifest = null;

        int minSdkVersion;
        if (mMinSdkVersion != null) {
            // No need to obtain minSdkVersion from the APK's AndroidManifest.xml
            minSdkVersion = mMinSdkVersion;
        } else {
            // Need to obtain minSdkVersion from the APK's AndroidManifest.xml
            if (androidManifest == null) {
                androidManifest = getAndroidManifestFromApk(apk, zipSections);
            }
            minSdkVersion =
                    ApkUtils.getMinSdkVersionFromBinaryAndroidManifest(androidManifest.slice());
            if (minSdkVersion > mMaxSdkVersion) {
                throw new IllegalArgumentException(
                        "minSdkVersion from APK (" + minSdkVersion + ") > maxSdkVersion ("
                                + mMaxSdkVersion + ")");
            }
        }

        Result result = new Result();

        // The SUPPORTED_APK_SIG_SCHEME_NAMES contains the mapping from version number to scheme
        // name, but the verifiers use this parameter as the schemes supported by the target SDK
        // range. Since the code below skips signature verification based on max SDK the mapping of
        // supported schemes needs to be modified to ensure the verifiers do not report a stripped
        // signature for an SDK range that does not support that signature version. For instance an
        // APK with V1, V2, and V3 signatures and a max SDK of O would skip the V3 signature
        // verification, but the SUPPORTED_APK_SIG_SCHEME_NAMES contains version 3, so when the V2
        // verification is performed it would see the stripping protection attribute, see that V3
        // is in the list of supported signatures, and report a stripped signature.
        Map<Integer, String> supportedSchemeNames;
        if (maxSdkVersion >= AndroidSdkVersion.P) {
            supportedSchemeNames = SUPPORTED_APK_SIG_SCHEME_NAMES;
        } else if (maxSdkVersion >= AndroidSdkVersion.N) {
            supportedSchemeNames = new HashMap<>(1);
            supportedSchemeNames.put(ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V2,
                    SUPPORTED_APK_SIG_SCHEME_NAMES.get(
                            ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V2));
        } else {
            supportedSchemeNames = Collections.EMPTY_MAP;
        }
        // Android N and newer attempts to verify APKs using the APK Signing Block, which can
        // include v2 and/or v3 signatures.  If none is found, it falls back to JAR signature
        // verification. If the signature is found but does not verify, the APK is rejected.
        Set<Integer> foundApkSigSchemeIds = new HashSet<>(2);
        if (maxSdkVersion >= AndroidSdkVersion.N) {
            RunnablesExecutor executor = RunnablesExecutor.SINGLE_THREADED;
            // Android P and newer attempts to verify APKs using APK Signature Scheme v3
            if (maxSdkVersion >= AndroidSdkVersion.P) {
                try {
                    ApkSigningBlockUtils.Result v3Result =
                            V3SchemeVerifier.verify(
                                    executor,
                                    apk,
                                    zipSections,
                                    Math.max(minSdkVersion, AndroidSdkVersion.P),
                                    maxSdkVersion);
                    foundApkSigSchemeIds.add(ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V3);
                    result.mergeFrom(v3Result);
                } catch (ApkSigningBlockUtils.SignatureNotFoundException ignored) {
                    // v3 signature not required
                }
                if (result.containsErrors()) {
                    return result;
                }
            }

            // Attempt to verify the APK using v2 signing if necessary. Platforms prior to Android P
            // ignore APK Signature Scheme v3 signatures and always attempt to verify either JAR or
            // APK Signature Scheme v2 signatures.  Android P onwards verifies v2 signatures only if
            // no APK Signature Scheme v3 (or newer scheme) signatures were found.
            if (minSdkVersion < AndroidSdkVersion.P || foundApkSigSchemeIds.isEmpty()) {
                try {
                    ApkSigningBlockUtils.Result v2Result =
                            V2SchemeVerifier.verify(
                                    executor,
                                    apk,
                                    zipSections,
                                    supportedSchemeNames,
                                    foundApkSigSchemeIds,
                                    Math.max(minSdkVersion, AndroidSdkVersion.N),
                                    maxSdkVersion);
                    foundApkSigSchemeIds.add(ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V2);
                    result.mergeFrom(v2Result);
                } catch (ApkSigningBlockUtils.SignatureNotFoundException ignored) {
                    // v2 signature not required
                }
                if (result.containsErrors()) {
                    return result;
                }
            }
        }

        // Android O and newer requires that APKs targeting security sandbox version 2 and higher
        // are signed using APK Signature Scheme v2 or newer.
        if (maxSdkVersion >= AndroidSdkVersion.O) {
            if (androidManifest == null) {
                androidManifest = getAndroidManifestFromApk(apk, zipSections);
            }
            int targetSandboxVersion =
                    getTargetSandboxVersionFromBinaryAndroidManifest(androidManifest.slice());
            if (targetSandboxVersion > 1) {
                if (foundApkSigSchemeIds.isEmpty()) {
                    result.addError(
                            Issue.NO_SIG_FOR_TARGET_SANDBOX_VERSION,
                            targetSandboxVersion);
                }
            }
        }

        // Attempt to verify the APK using JAR signing if necessary. Platforms prior to Android N
        // ignore APK Signature Scheme v2 signatures and always attempt to verify JAR signatures.
        // Android N onwards verifies JAR signatures only if no APK Signature Scheme v2 (or newer
        // scheme) signatures were found.
        if ((minSdkVersion < AndroidSdkVersion.N) || (foundApkSigSchemeIds.isEmpty())) {
            V1SchemeVerifier.Result v1Result =
                    V1SchemeVerifier.verify(
                            apk,
                            zipSections,
                            supportedSchemeNames,
                            foundApkSigSchemeIds,
                            minSdkVersion,
                            maxSdkVersion);
            result.mergeFrom(v1Result);
        }
        if (result.containsErrors()) {
            return result;
        }

        // Check whether v1 and v2 scheme signer identifies match, provided both v1 and v2
        // signatures verified.
        if ((result.isVerifiedUsingV1Scheme()) && (result.isVerifiedUsingV2Scheme())) {
            ArrayList<Result.V1SchemeSignerInfo> v1Signers =
                    new ArrayList<>(result.getV1SchemeSigners());
            ArrayList<Result.V2SchemeSignerInfo> v2Signers =
                    new ArrayList<>(result.getV2SchemeSigners());
            ArrayList<ByteArray> v1SignerCerts = new ArrayList<>();
            ArrayList<ByteArray> v2SignerCerts = new ArrayList<>();
            for (Result.V1SchemeSignerInfo signer : v1Signers) {
                try {
                    v1SignerCerts.add(new ByteArray(signer.getCertificate().getEncoded()));
                } catch (CertificateEncodingException e) {
                    throw new RuntimeException(
                            "Failed to encode JAR signer " + signer.getName() + " certs", e);
                }
            }
            for (Result.V2SchemeSignerInfo signer : v2Signers) {
                try {
                    v2SignerCerts.add(new ByteArray(signer.getCertificate().getEncoded()));
                } catch (CertificateEncodingException e) {
                    throw new RuntimeException(
                            "Failed to encode APK Signature Scheme v2 signer (index: "
                                    + signer.getIndex() + ") certs",
                            e);
                }
            }

            for (int i = 0; i < v1SignerCerts.size(); i++) {
                ByteArray v1Cert = v1SignerCerts.get(i);
                if (!v2SignerCerts.contains(v1Cert)) {
                    Result.V1SchemeSignerInfo v1Signer = v1Signers.get(i);
                    v1Signer.addError(Issue.V2_SIG_MISSING);
                    break;
                }
            }
            for (int i = 0; i < v2SignerCerts.size(); i++) {
                ByteArray v2Cert = v2SignerCerts.get(i);
                if (!v1SignerCerts.contains(v2Cert)) {
                    Result.V2SchemeSignerInfo v2Signer = v2Signers.get(i);
                    v2Signer.addError(Issue.JAR_SIG_MISSING);
                    break;
                }
            }
        }

        // If there is a v3 scheme signer and an earlier scheme signer, make sure that there is a
        // match, or in the event of signing certificate rotation, that the v1/v2 scheme signer
        // matches the oldest signing certificate in the provided SigningCertificateLineage
        if (result.isVerifiedUsingV3Scheme()
                && (result.isVerifiedUsingV1Scheme() || result.isVerifiedUsingV2Scheme())) {
            SigningCertificateLineage lineage = result.getSigningCertificateLineage();
            X509Certificate oldSignerCert;
            if (result.isVerifiedUsingV1Scheme()) {
                List<Result.V1SchemeSignerInfo> v1Signers = result.getV1SchemeSigners();
                if (v1Signers.size() != 1) {
                    // APK Signature Scheme v3 only supports single-signers, error to sign with
                    // multiple and then only one
                    result.addError(Issue.V3_SIG_MULTIPLE_PAST_SIGNERS);
                }
                oldSignerCert = v1Signers.get(0).mCertChain.get(0);
            } else {
                List<Result.V2SchemeSignerInfo> v2Signers = result.getV2SchemeSigners();
                if (v2Signers.size() != 1) {
                    // APK Signature Scheme v3 only supports single-signers, error to sign with
                    // multiple and then only one
                    result.addError(Issue.V3_SIG_MULTIPLE_PAST_SIGNERS);
                }
                oldSignerCert = v2Signers.get(0).mCerts.get(0);
            }
            if (lineage == null) {
                // no signing certificate history with which to contend, just make sure that v3
                // matches previous versions
                List<Result.V3SchemeSignerInfo> v3Signers = result.getV3SchemeSigners();
                if (v3Signers.size() != 1) {
                    // multiple v3 signers should never exist without rotation history, since
                    // multiple signers implies a different signer for different platform versions
                    result.addError(Issue.V3_SIG_MULTIPLE_SIGNERS);
                }
                try {
                    if (!Arrays.equals(oldSignerCert.getEncoded(),
                           v3Signers.get(0).mCerts.get(0).getEncoded())) {
                        result.addError(Issue.V3_SIG_PAST_SIGNERS_MISMATCH);
                    }
                } catch (CertificateEncodingException e) {
                    // we just go the encoding for the v1/v2 certs above, so must be v3
                    throw new RuntimeException(
                            "Failed to encode APK Signature Scheme v3 signer cert", e);
                }
            } else {
                // we have some signing history, make sure that the root of the history is the same
                // as our v1/v2 signer
                try {
                    lineage = lineage.getSubLineage(oldSignerCert);
                    if (lineage.size() != 1) {
                        // the v1/v2 signer was found, but not at the root of the lineage
                        result.addError(Issue.V3_SIG_PAST_SIGNERS_MISMATCH);
                    }
                } catch (IllegalArgumentException e) {
                    // the v1/v2 signer was not found in the lineage
                    result.addError(Issue.V3_SIG_PAST_SIGNERS_MISMATCH);
                }
            }
        }

        if (result.containsErrors()) {
            return result;
        }

        // Verified
        result.setVerified();
        if (result.isVerifiedUsingV3Scheme()) {
            List<Result.V3SchemeSignerInfo> v3Signers = result.getV3SchemeSigners();
            result.addSignerCertificate(v3Signers.get(v3Signers.size() - 1).getCertificate());
        } else if (result.isVerifiedUsingV2Scheme()) {
            for (Result.V2SchemeSignerInfo signerInfo : result.getV2SchemeSigners()) {
                result.addSignerCertificate(signerInfo.getCertificate());
            }
        } else if (result.isVerifiedUsingV1Scheme()) {
            for (Result.V1SchemeSignerInfo signerInfo : result.getV1SchemeSigners()) {
                result.addSignerCertificate(signerInfo.getCertificate());
            }
        } else {
            throw new RuntimeException(
                    "APK verified, but has not verified using any of v1, v2 or v3schemes");
        }

        return result;
    }

    private static ByteBuffer getAndroidManifestFromApk(
            DataSource apk, ApkUtils.ZipSections zipSections)
                    throws IOException, ApkFormatException {
        List<CentralDirectoryRecord> cdRecords =
                V1SchemeVerifier.parseZipCentralDirectory(apk, zipSections);
        try {
            return ApkSigner.getAndroidManifestFromApk(
                    cdRecords,
                    apk.slice(0, zipSections.getZipCentralDirectoryOffset()));
        } catch (ZipFormatException e) {
            throw new ApkFormatException("Failed to read AndroidManifest.xml", e);
        }
    }

    /**
     * Android resource ID of the {@code android:targetSandboxVersion} attribute in
     * AndroidManifest.xml.
     */
    private static final int TARGET_SANDBOX_VERSION_ATTR_ID = 0x0101054c;

    /**
     * Returns the security sandbox version targeted by an APK with the provided
     * {@code AndroidManifest.xml}.
     *
     * @param androidManifestContents contents of {@code AndroidManifest.xml} in binary Android
     *        resource format
     *
     * @throws ApkFormatException if an error occurred while determining the version
     */
    private static int getTargetSandboxVersionFromBinaryAndroidManifest(
            ByteBuffer androidManifestContents) throws ApkFormatException {
        // Return the value of the android:targetSandboxVersion attribute of the top-level manifest
        // element
        try {
            AndroidBinXmlParser parser = new AndroidBinXmlParser(androidManifestContents);
            int eventType = parser.getEventType();
            while (eventType != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
                if ((eventType == AndroidBinXmlParser.EVENT_START_ELEMENT)
                        && (parser.getDepth() == 1)
                        && ("manifest".equals(parser.getName()))
                        && (parser.getNamespace().isEmpty())) {
                    // In each manifest element, targetSandboxVersion defaults to 1
                    int result = 1;
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeNameResourceId(i)
                                == TARGET_SANDBOX_VERSION_ATTR_ID) {
                            int valueType = parser.getAttributeValueType(i);
                            switch (valueType) {
                                case AndroidBinXmlParser.VALUE_TYPE_INT:
                                    result = parser.getAttributeIntValue(i);
                                    break;
                                default:
                                    throw new ApkFormatException(
                                            "Failed to determine APK's target sandbox version"
                                                    + ": unsupported value type of"
                                                    + " AndroidManifest.xml"
                                                    + " android:targetSandboxVersion"
                                                    + ". Only integer values supported.");
                            }
                            break;
                        }
                    }
                    return result;
                }
                eventType = parser.next();
            }
            throw new ApkFormatException(
                    "Failed to determine APK's target sandbox version"
                            + " : no manifest element in AndroidManifest.xml");
        } catch (AndroidBinXmlParser.XmlParserException e) {
            throw new ApkFormatException(
                    "Failed to determine APK's target sandbox version"
                            + ": malformed AndroidManifest.xml",
                    e);
        }
    }

    /**
     * Result of verifying an APKs signatures. The APK can be considered verified iff
     * {@link #isVerified()} returns {@code true}.
     */
    public static class Result {
        private final List<IssueWithParams> mErrors = new ArrayList<>();
        private final List<IssueWithParams> mWarnings = new ArrayList<>();
        private final List<X509Certificate> mSignerCerts = new ArrayList<>();
        private final List<V1SchemeSignerInfo> mV1SchemeSigners = new ArrayList<>();
        private final List<V1SchemeSignerInfo> mV1SchemeIgnoredSigners = new ArrayList<>();
        private final List<V2SchemeSignerInfo> mV2SchemeSigners = new ArrayList<>();
        private final List<V3SchemeSignerInfo> mV3SchemeSigners = new ArrayList<>();

        private boolean mVerified;
        private boolean mVerifiedUsingV1Scheme;
        private boolean mVerifiedUsingV2Scheme;
        private boolean mVerifiedUsingV3Scheme;
        private SigningCertificateLineage mSigningCertificateLineage;

        /**
         * Returns {@code true} if the APK's signatures verified.
         */
        public boolean isVerified() {
            return mVerified;
        }

        private void setVerified() {
            mVerified = true;
        }

        /**
         * Returns {@code true} if the APK's JAR signatures verified.
         */
        public boolean isVerifiedUsingV1Scheme() {
            return mVerifiedUsingV1Scheme;
        }

        /**
         * Returns {@code true} if the APK's APK Signature Scheme v2 signatures verified.
         */
        public boolean isVerifiedUsingV2Scheme() {
            return mVerifiedUsingV2Scheme;
        }

        /**
         * Returns {@code true} if the APK's APK Signature Scheme v3 signature verified.
         */
        public boolean isVerifiedUsingV3Scheme() {
            return mVerifiedUsingV3Scheme;
        }

        /**
         * Returns the verified signers' certificates, one per signer.
         */
        public List<X509Certificate> getSignerCertificates() {
            return mSignerCerts;
        }

        private void addSignerCertificate(X509Certificate cert) {
            mSignerCerts.add(cert);
        }

        /**
         * Returns information about JAR signers associated with the APK's signature. These are the
         * signers used by Android.
         *
         * @see #getV1SchemeIgnoredSigners()
         */
        public List<V1SchemeSignerInfo> getV1SchemeSigners() {
            return mV1SchemeSigners;
        }

        /**
         * Returns information about JAR signers ignored by the APK's signature verification
         * process. These signers are ignored by Android. However, each signer's errors or warnings
         * will contain information about why they are ignored.
         *
         * @see #getV1SchemeSigners()
         */
        public List<V1SchemeSignerInfo> getV1SchemeIgnoredSigners() {
            return mV1SchemeIgnoredSigners;
        }

        /**
         * Returns information about APK Signature Scheme v2 signers associated with the APK's
         * signature.
         */
        public List<V2SchemeSignerInfo> getV2SchemeSigners() {
            return mV2SchemeSigners;
        }

        /**
         * Returns information about APK Signature Scheme v3 signers associated with the APK's
         * signature.
         *
         * <note> Multiple signers represent different targeted platform versions, not
         * a signing identity of multiple signers.  APK Signature Scheme v3 only supports single
         * signer identities.</note>
         */
        public List<V3SchemeSignerInfo> getV3SchemeSigners() {
            return mV3SchemeSigners;
        }

        /**
         * Returns the combined SigningCertificateLineage associated with this APK's APK Signature
         * Scheme v3 signing block.
         */
        public SigningCertificateLineage getSigningCertificateLineage() {
            return mSigningCertificateLineage;
        }

        void addError(Issue msg, Object... parameters) {
            mErrors.add(new IssueWithParams(msg, parameters));
        }

        /**
         * Returns errors encountered while verifying the APK's signatures.
         */
        public List<IssueWithParams> getErrors() {
            return mErrors;
        }

        /**
         * Returns warnings encountered while verifying the APK's signatures.
         */
        public List<IssueWithParams> getWarnings() {
            return mWarnings;
        }

        private void mergeFrom(V1SchemeVerifier.Result source) {
            mVerifiedUsingV1Scheme = source.verified;
            mErrors.addAll(source.getErrors());
            mWarnings.addAll(source.getWarnings());
            for (V1SchemeVerifier.Result.SignerInfo signer : source.signers) {
                mV1SchemeSigners.add(new V1SchemeSignerInfo(signer));
            }
            for (V1SchemeVerifier.Result.SignerInfo signer : source.ignoredSigners) {
                mV1SchemeIgnoredSigners.add(new V1SchemeSignerInfo(signer));
            }
        }

        private void mergeFrom(ApkSigningBlockUtils.Result source) {
            switch (source.signatureSchemeVersion) {
                case ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V2:
                    mVerifiedUsingV2Scheme = source.verified;
                    for (ApkSigningBlockUtils.Result.SignerInfo signer : source.signers) {
                        mV2SchemeSigners.add(new V2SchemeSignerInfo(signer));
                    }
                    break;
                case ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V3:
                    mVerifiedUsingV3Scheme = source.verified;
                    for (ApkSigningBlockUtils.Result.SignerInfo signer : source.signers) {
                        mV3SchemeSigners.add(new V3SchemeSignerInfo(signer));
                    }
                    mSigningCertificateLineage = source.signingCertificateLineage;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown Signing Block Scheme Id");
            }
            mErrors.addAll(source.getErrors());
            mWarnings.addAll(source.getWarnings());
        }

        /**
         * Returns {@code true} if an error was encountered while verifying the APK. Any error
         * prevents the APK from being considered verified.
         */
        public boolean containsErrors() {
            if (!mErrors.isEmpty()) {
                return true;
            }
            if (!mV1SchemeSigners.isEmpty()) {
                for (V1SchemeSignerInfo signer : mV1SchemeSigners) {
                    if (signer.containsErrors()) {
                        return true;
                    }
                }
            }
            if (!mV2SchemeSigners.isEmpty()) {
                for (V2SchemeSignerInfo signer : mV2SchemeSigners) {
                    if (signer.containsErrors()) {
                        return true;
                    }
                }
            }
            if (!mV3SchemeSigners.isEmpty()) {
                for (V3SchemeSignerInfo signer : mV3SchemeSigners) {
                    if (signer.containsErrors()) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * Information about a JAR signer associated with the APK's signature.
         */
        public static class V1SchemeSignerInfo {
            private final String mName;
            private final List<X509Certificate> mCertChain;
            private final String mSignatureBlockFileName;
            private final String mSignatureFileName;

            private final List<IssueWithParams> mErrors;
            private final List<IssueWithParams> mWarnings;

            private V1SchemeSignerInfo(V1SchemeVerifier.Result.SignerInfo result) {
                mName = result.name;
                mCertChain = result.certChain;
                mSignatureBlockFileName = result.signatureBlockFileName;
                mSignatureFileName = result.signatureFileName;
                mErrors = result.getErrors();
                mWarnings = result.getWarnings();
            }

            /**
             * Returns a user-friendly name of the signer.
             */
            public String getName() {
                return mName;
            }

            /**
             * Returns the name of the JAR entry containing this signer's JAR signature block file.
             */
            public String getSignatureBlockFileName() {
                return mSignatureBlockFileName;
            }

            /**
             * Returns the name of the JAR entry containing this signer's JAR signature file.
             */
            public String getSignatureFileName() {
                return mSignatureFileName;
            }

            /**
             * Returns this signer's signing certificate or {@code null} if not available. The
             * certificate is guaranteed to be available if no errors were encountered during
             * verification (see {@link #containsErrors()}.
             *
             * <p>This certificate contains the signer's public key.
             */
            public X509Certificate getCertificate() {
                return mCertChain.isEmpty() ? null : mCertChain.get(0);
            }

            /**
             * Returns the certificate chain for the signer's public key. The certificate containing
             * the public key is first, followed by the certificate (if any) which issued the
             * signing certificate, and so forth. An empty list may be returned if an error was
             * encountered during verification (see {@link #containsErrors()}).
             */
            public List<X509Certificate> getCertificateChain() {
                return mCertChain;
            }

            /**
             * Returns {@code true} if an error was encountered while verifying this signer's JAR
             * signature. Any error prevents the signer's signature from being considered verified.
             */
            public boolean containsErrors() {
                return !mErrors.isEmpty();
            }

            /**
             * Returns errors encountered while verifying this signer's JAR signature. Any error
             * prevents the signer's signature from being considered verified.
             */
            public List<IssueWithParams> getErrors() {
                return mErrors;
            }

            /**
             * Returns warnings encountered while verifying this signer's JAR signature. Warnings
             * do not prevent the signer's signature from being considered verified.
             */
            public List<IssueWithParams> getWarnings() {
                return mWarnings;
            }

            private void addError(Issue msg, Object... parameters) {
                mErrors.add(new IssueWithParams(msg, parameters));
            }
        }

        /**
         * Information about an APK Signature Scheme v2 signer associated with the APK's signature.
         */
        public static class V2SchemeSignerInfo {
            private final int mIndex;
            private final List<X509Certificate> mCerts;

            private final List<IssueWithParams> mErrors;
            private final List<IssueWithParams> mWarnings;

            private V2SchemeSignerInfo(ApkSigningBlockUtils.Result.SignerInfo result) {
                mIndex = result.index;
                mCerts = result.certs;
                mErrors = result.getErrors();
                mWarnings = result.getWarnings();
            }

            /**
             * Returns this signer's {@code 0}-based index in the list of signers contained in the
             * APK's APK Signature Scheme v2 signature.
             */
            public int getIndex() {
                return mIndex;
            }

            /**
             * Returns this signer's signing certificate or {@code null} if not available. The
             * certificate is guaranteed to be available if no errors were encountered during
             * verification (see {@link #containsErrors()}.
             *
             * <p>This certificate contains the signer's public key.
             */
            public X509Certificate getCertificate() {
                return mCerts.isEmpty() ? null : mCerts.get(0);
            }

            /**
             * Returns this signer's certificates. The first certificate is for the signer's public
             * key. An empty list may be returned if an error was encountered during verification
             * (see {@link #containsErrors()}).
             */
            public List<X509Certificate> getCertificates() {
                return mCerts;
            }

            private void addError(Issue msg, Object... parameters) {
                mErrors.add(new IssueWithParams(msg, parameters));
            }

            public boolean containsErrors() {
                return !mErrors.isEmpty();
            }

            public List<IssueWithParams> getErrors() {
                return mErrors;
            }

            public List<IssueWithParams> getWarnings() {
                return mWarnings;
            }
        }

        /**
         * Information about an APK Signature Scheme v3 signer associated with the APK's signature.
         */
        public static class V3SchemeSignerInfo {
            private final int mIndex;
            private final List<X509Certificate> mCerts;

            private final List<IssueWithParams> mErrors;
            private final List<IssueWithParams> mWarnings;

            private V3SchemeSignerInfo(ApkSigningBlockUtils.Result.SignerInfo result) {
                mIndex = result.index;
                mCerts = result.certs;
                mErrors = result.getErrors();
                mWarnings = result.getWarnings();
            }

            /**
             * Returns this signer's {@code 0}-based index in the list of signers contained in the
             * APK's APK Signature Scheme v3 signature.
             */
            public int getIndex() {
                return mIndex;
            }

            /**
             * Returns this signer's signing certificate or {@code null} if not available. The
             * certificate is guaranteed to be available if no errors were encountered during
             * verification (see {@link #containsErrors()}.
             *
             * <p>This certificate contains the signer's public key.
             */
            public X509Certificate getCertificate() {
                return mCerts.isEmpty() ? null : mCerts.get(0);
            }

            /**
             * Returns this signer's certificates. The first certificate is for the signer's public
             * key. An empty list may be returned if an error was encountered during verification
             * (see {@link #containsErrors()}).
             */
            public List<X509Certificate> getCertificates() {
                return mCerts;
            }

            public boolean containsErrors() {
                return !mErrors.isEmpty();
            }

            public List<IssueWithParams> getErrors() {
                return mErrors;
            }

            public List<IssueWithParams> getWarnings() {
                return mWarnings;
            }
        }
    }

    /**
     * Error or warning encountered while verifying an APK's signatures.
     */
    public static enum Issue {

        /**
         * APK is not JAR-signed.
         */
        JAR_SIG_NO_SIGNATURES("No JAR signatures"),

        /**
         * APK does not contain any entries covered by JAR signatures.
         */
        JAR_SIG_NO_SIGNED_ZIP_ENTRIES("No JAR entries covered by JAR signatures"),

        /**
         * APK contains multiple entries with the same name.
         *
         * <ul>
         * <li>Parameter 1: name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_DUPLICATE_ZIP_ENTRY("Duplicate entry: %1$s"),

        /**
         * JAR manifest contains a section with a duplicate name.
         *
         * <ul>
         * <li>Parameter 1: section name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_DUPLICATE_MANIFEST_SECTION("Duplicate section in META-INF/MANIFEST.MF: %1$s"),

        /**
         * JAR manifest contains a section without a name.
         *
         * <ul>
         * <li>Parameter 1: section index (1-based) ({@code Integer})</li>
         * </ul>
         */
        JAR_SIG_UNNNAMED_MANIFEST_SECTION(
                "Malformed META-INF/MANIFEST.MF: invidual section #%1$d does not have a name"),

        /**
         * JAR signature file contains a section without a name.
         *
         * <ul>
         * <li>Parameter 1: signature file name ({@code String})</li>
         * <li>Parameter 2: section index (1-based) ({@code Integer})</li>
         * </ul>
         */
        JAR_SIG_UNNNAMED_SIG_FILE_SECTION(
                "Malformed %1$s: invidual section #%2$d does not have a name"),

        /** APK is missing the JAR manifest entry (META-INF/MANIFEST.MF). */
        JAR_SIG_NO_MANIFEST("Missing META-INF/MANIFEST.MF"),

        /**
         * JAR manifest references an entry which is not there in the APK.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MISSING_ZIP_ENTRY_REFERENCED_IN_MANIFEST(
                "%1$s entry referenced by META-INF/MANIFEST.MF not found in the APK"),

        /**
         * JAR manifest does not list a digest for the specified entry.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_ZIP_ENTRY_DIGEST_IN_MANIFEST("No digest for %1$s in META-INF/MANIFEST.MF"),

        /**
         * JAR signature does not list a digest for the specified entry.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * <li>Parameter 2: signature file name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_ZIP_ENTRY_DIGEST_IN_SIG_FILE("No digest for %1$s in %2$s"),

        /**
         * The specified JAR entry is not covered by JAR signature.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_ZIP_ENTRY_NOT_SIGNED("%1$s entry not signed"),

        /**
         * JAR signature uses different set of signers to protect the two specified ZIP entries.
         *
         * <ul>
         * <li>Parameter 1: first entry name ({@code String})</li>
         * <li>Parameter 2: first entry signer names ({@code List<String>})</li>
         * <li>Parameter 3: second entry name ({@code String})</li>
         * <li>Parameter 4: second entry signer names ({@code List<String>})</li>
         * </ul>
         */
        JAR_SIG_ZIP_ENTRY_SIGNERS_MISMATCH(
                "Entries %1$s and %3$s are signed with different sets of signers"
                        + " : <%2$s> vs <%4$s>"),

        /**
         * Digest of the specified ZIP entry's data does not match the digest expected by the JAR
         * signature.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * <li>Parameter 2: digest algorithm (e.g., SHA-256) ({@code String})</li>
         * <li>Parameter 3: name of the entry in which the expected digest is specified
         *     ({@code String})</li>
         * <li>Parameter 4: base64-encoded actual digest ({@code String})</li>
         * <li>Parameter 5: base64-encoded expected digest ({@code String})</li>
         * </ul>
         */
        JAR_SIG_ZIP_ENTRY_DIGEST_DID_NOT_VERIFY(
                "%2$s digest of %1$s does not match the digest specified in %3$s"
                        + ". Expected: <%5$s>, actual: <%4$s>"),

        /**
         * Digest of the JAR manifest main section did not verify.
         *
         * <ul>
         * <li>Parameter 1: digest algorithm (e.g., SHA-256) ({@code String})</li>
         * <li>Parameter 2: name of the entry in which the expected digest is specified
         *     ({@code String})</li>
         * <li>Parameter 3: base64-encoded actual digest ({@code String})</li>
         * <li>Parameter 4: base64-encoded expected digest ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MANIFEST_MAIN_SECTION_DIGEST_DID_NOT_VERIFY(
                "%1$s digest of META-INF/MANIFEST.MF main section does not match the digest"
                        + " specified in %2$s. Expected: <%4$s>, actual: <%3$s>"),

        /**
         * Digest of the specified JAR manifest section does not match the digest expected by the
         * JAR signature.
         *
         * <ul>
         * <li>Parameter 1: section name ({@code String})</li>
         * <li>Parameter 2: digest algorithm (e.g., SHA-256) ({@code String})</li>
         * <li>Parameter 3: name of the signature file in which the expected digest is specified
         *     ({@code String})</li>
         * <li>Parameter 4: base64-encoded actual digest ({@code String})</li>
         * <li>Parameter 5: base64-encoded expected digest ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MANIFEST_SECTION_DIGEST_DID_NOT_VERIFY(
                "%2$s digest of META-INF/MANIFEST.MF section for %1$s does not match the digest"
                        + " specified in %3$s. Expected: <%5$s>, actual: <%4$s>"),

        /**
         * JAR signature file does not contain the whole-file digest of the JAR manifest file. The
         * digest speeds up verification of JAR signature.
         *
         * <ul>
         * <li>Parameter 1: name of the signature file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_MANIFEST_DIGEST_IN_SIG_FILE(
                "%1$s does not specify digest of META-INF/MANIFEST.MF"
                        + ". This slows down verification."),

        /**
         * APK is signed using APK Signature Scheme v2 or newer, but JAR signature file does not
         * contain protections against stripping of these newer scheme signatures.
         *
         * <ul>
         * <li>Parameter 1: name of the signature file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_APK_SIG_STRIP_PROTECTION(
                "APK is signed using APK Signature Scheme v2 but these signatures may be stripped"
                        + " without being detected because %1$s does not contain anti-stripping"
                        + " protections."),

        /**
         * JAR signature of the signer is missing a file/entry.
         *
         * <ul>
         * <li>Parameter 1: name of the encountered file ({@code String})</li>
         * <li>Parameter 2: name of the missing file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MISSING_FILE("Partial JAR signature. Found: %1$s, missing: %2$s"),

        /**
         * An exception was encountered while verifying JAR signature contained in a signature block
         * against the signature file.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: name of the signature file ({@code String})</li>
         * <li>Parameter 3: exception ({@code Throwable})</li>
         * </ul>
         */
        JAR_SIG_VERIFY_EXCEPTION("Failed to verify JAR signature %1$s against %2$s: %3$s"),

        /**
         * JAR signature contains unsupported digest algorithm.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: digest algorithm OID ({@code String})</li>
         * <li>Parameter 3: signature algorithm OID ({@code String})</li>
         * <li>Parameter 4: API Levels on which this combination of algorithms is not supported
         *     ({@code String})</li>
         * <li>Parameter 5: user-friendly variant of digest algorithm ({@code String})</li>
         * <li>Parameter 6: user-friendly variant of signature algorithm ({@code String})</li>
         * </ul>
         */
        JAR_SIG_UNSUPPORTED_SIG_ALG(
                "JAR signature %1$s uses digest algorithm %5$s and signature algorithm %6$s which"
                        + " is not supported on API Level(s) %4$s for which this APK is being"
                        + " verified"),

        /**
         * An exception was encountered while parsing JAR signature contained in a signature block.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: exception ({@code Throwable})</li>
         * </ul>
         */
        JAR_SIG_PARSE_EXCEPTION("Failed to parse JAR signature %1$s: %2$s"),

        /**
         * An exception was encountered while parsing a certificate contained in the JAR signature
         * block.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: exception ({@code Throwable})</li>
         * </ul>
         */
        JAR_SIG_MALFORMED_CERTIFICATE("Malformed certificate in JAR signature %1$s: %2$s"),

        /**
         * JAR signature contained in a signature block file did not verify against the signature
         * file.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: name of the signature file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_DID_NOT_VERIFY("JAR signature %1$s did not verify against %2$s"),

        /**
         * JAR signature contains no verified signers.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_SIGNERS("JAR signature %1$s contains no signers"),

        /**
         * JAR signature file contains a section with a duplicate name.
         *
         * <ul>
         * <li>Parameter 1: signature file name ({@code String})</li>
         * <li>Parameter 1: section name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_DUPLICATE_SIG_FILE_SECTION("Duplicate section in %1$s: %2$s"),

        /**
         * JAR signature file's main section doesn't contain the mandatory Signature-Version
         * attribute.
         *
         * <ul>
         * <li>Parameter 1: signature file name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MISSING_VERSION_ATTR_IN_SIG_FILE(
                "Malformed %1$s: missing Signature-Version attribute"),

        /**
         * JAR signature file references an unknown APK signature scheme ID.
         *
         * <ul>
         * <li>Parameter 1: name of the signature file ({@code String})</li>
         * <li>Parameter 2: unknown APK signature scheme ID ({@code} Integer)</li>
         * </ul>
         */
        JAR_SIG_UNKNOWN_APK_SIG_SCHEME_ID(
                "JAR signature %1$s references unknown APK signature scheme ID: %2$d"),

        /**
         * JAR signature file indicates that the APK is supposed to be signed with a supported APK
         * signature scheme (in addition to the JAR signature) but no such signature was found in
         * the APK.
         *
         * <ul>
         * <li>Parameter 1: name of the signature file ({@code String})</li>
         * <li>Parameter 2: APK signature scheme ID ({@code} Integer)</li>
         * <li>Parameter 3: APK signature scheme English name ({@code} String)</li>
         * </ul>
         */
        JAR_SIG_MISSING_APK_SIG_REFERENCED(
                "JAR signature %1$s indicates the APK is signed using %3$s but no such signature"
                        + " was found. Signature stripped?"),

        /**
         * JAR entry is not covered by signature and thus unauthorized modifications to its contents
         * will not be detected.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_UNPROTECTED_ZIP_ENTRY(
                "%1$s not protected by signature. Unauthorized modifications to this JAR entry"
                        + " will not be detected. Delete or move the entry outside of META-INF/."),

        /**
         * APK which is both JAR-signed and signed using APK Signature Scheme v2 contains an APK
         * Signature Scheme v2 signature from this signer, but does not contain a JAR signature
         * from this signer.
         */
        JAR_SIG_MISSING("No JAR signature from this signer"),

        /**
         * APK is targeting a sandbox version which requires APK Signature Scheme v2 signature but
         * no such signature was found.
         *
         * <ul>
         * <li>Parameter 1: target sandbox version ({@code Integer})</li>
         * </ul>
         */
        NO_SIG_FOR_TARGET_SANDBOX_VERSION(
                "Missing APK Signature Scheme v2 signature required for target sandbox version"
                        + " %1$d"),

        /**
         * APK which is both JAR-signed and signed using APK Signature Scheme v2 contains a JAR
         * signature from this signer, but does not contain an APK Signature Scheme v2 signature
         * from this signer.
         */
        V2_SIG_MISSING("No APK Signature Scheme v2 signature from this signer"),

        /**
         * Failed to parse the list of signers contained in the APK Signature Scheme v2 signature.
         */
        V2_SIG_MALFORMED_SIGNERS("Malformed list of signers"),

        /**
         * Failed to parse this signer's signer block contained in the APK Signature Scheme v2
         * signature.
         */
        V2_SIG_MALFORMED_SIGNER("Malformed signer block"),

        /**
         * Public key embedded in the APK Signature Scheme v2 signature of this signer could not be
         * parsed.
         *
         * <ul>
         * <li>Parameter 1: error details ({@code Throwable})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_PUBLIC_KEY("Malformed public key: %1$s"),

        /**
         * This APK Signature Scheme v2 signer's certificate could not be parsed.
         *
         * <ul>
         * <li>Parameter 1: index ({@code 0}-based) of the certificate in the signer's list of
         *     certificates ({@code Integer})</li>
         * <li>Parameter 2: sequence number ({@code 1}-based) of the certificate in the signer's
         *     list of certificates ({@code Integer})</li>
         * <li>Parameter 3: error details ({@code Throwable})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_CERTIFICATE("Malformed certificate #%2$d: %3$s"),

        /**
         * Failed to parse this signer's signature record contained in the APK Signature Scheme v2
         * signature.
         *
         * <ul>
         * <li>Parameter 1: record number (first record is {@code 1}) ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_SIGNATURE("Malformed APK Signature Scheme v2 signature record #%1$d"),

        /**
         * Failed to parse this signer's digest record contained in the APK Signature Scheme v2
         * signature.
         *
         * <ul>
         * <li>Parameter 1: record number (first record is {@code 1}) ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_DIGEST("Malformed APK Signature Scheme v2 digest record #%1$d"),

        /**
         * This APK Signature Scheme v2 signer contains a malformed additional attribute.
         *
         * <ul>
         * <li>Parameter 1: attribute number (first attribute is {@code 1}) {@code Integer})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_ADDITIONAL_ATTRIBUTE("Malformed additional attribute #%1$d"),

        /**
         * APK Signature Scheme v2 signature references an unknown APK signature scheme ID.
         *
         * <ul>
         * <li>Parameter 1: signer index ({@code Integer})</li>
         * <li>Parameter 2: unknown APK signature scheme ID ({@code} Integer)</li>
         * </ul>
         */
        V2_SIG_UNKNOWN_APK_SIG_SCHEME_ID(
                "APK Signature Scheme v2 signer: %1$s references unknown APK signature scheme ID: "
                        + "%2$d"),

        /**
         * APK Signature Scheme v2 signature indicates that the APK is supposed to be signed with a
         * supported APK signature scheme (in addition to the v2 signature) but no such signature
         * was found in the APK.
         *
         * <ul>
         * <li>Parameter 1: signer index ({@code Integer})</li>
         * <li>Parameter 2: APK signature scheme English name ({@code} String)</li>
         * </ul>
         */
        V2_SIG_MISSING_APK_SIG_REFERENCED(
                "APK Signature Scheme v2 signature %1$s indicates the APK is signed using %2$s but "
                        + "no such signature was found. Signature stripped?"),

        /**
         * APK Signature Scheme v2 signature contains no signers.
         */
        V2_SIG_NO_SIGNERS("No signers in APK Signature Scheme v2 signature"),

        /**
         * This APK Signature Scheme v2 signer contains a signature produced using an unknown
         * algorithm.
         *
         * <ul>
         * <li>Parameter 1: algorithm ID ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_UNKNOWN_SIG_ALGORITHM("Unknown signature algorithm: %1$#x"),

        /**
         * This APK Signature Scheme v2 signer contains an unknown additional attribute.
         *
         * <ul>
         * <li>Parameter 1: attribute ID ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_UNKNOWN_ADDITIONAL_ATTRIBUTE("Unknown additional attribute: ID %1$#x"),

        /**
         * An exception was encountered while verifying APK Signature Scheme v2 signature of this
         * signer.
         *
         * <ul>
         * <li>Parameter 1: signature algorithm ({@link SignatureAlgorithm})</li>
         * <li>Parameter 2: exception ({@code Throwable})</li>
         * </ul>
         */
        V2_SIG_VERIFY_EXCEPTION("Failed to verify %1$s signature: %2$s"),

        /**
         * APK Signature Scheme v2 signature over this signer's signed-data block did not verify.
         *
         * <ul>
         * <li>Parameter 1: signature algorithm ({@link SignatureAlgorithm})</li>
         * </ul>
         */
        V2_SIG_DID_NOT_VERIFY("%1$s signature over signed-data did not verify"),

        /**
         * This APK Signature Scheme v2 signer offers no signatures.
         */
        V2_SIG_NO_SIGNATURES("No signatures"),

        /**
         * This APK Signature Scheme v2 signer offers signatures but none of them are supported.
         */
        V2_SIG_NO_SUPPORTED_SIGNATURES("No supported signatures"),

        /**
         * This APK Signature Scheme v2 signer offers no certificates.
         */
        V2_SIG_NO_CERTIFICATES("No certificates"),

        /**
         * This APK Signature Scheme v2 signer's public key listed in the signer's certificate does
         * not match the public key listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: hex-encoded public key from certificate ({@code String})</li>
         * <li>Parameter 2: hex-encoded public key from signatures record ({@code String})</li>
         * </ul>
         */
        V2_SIG_PUBLIC_KEY_MISMATCH_BETWEEN_CERTIFICATE_AND_SIGNATURES_RECORD(
                "Public key mismatch between certificate and signature record: <%1$s> vs <%2$s>"),

        /**
         * This APK Signature Scheme v2 signer's signature algorithms listed in the signatures
         * record do not match the signature algorithms listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: signature algorithms from signatures record ({@code List<Integer>})</li>
         * <li>Parameter 2: signature algorithms from digests record ({@code List<Integer>})</li>
         * </ul>
         */
        V2_SIG_SIG_ALG_MISMATCH_BETWEEN_SIGNATURES_AND_DIGESTS_RECORDS(
                "Signature algorithms mismatch between signatures and digests records"
                        + ": %1$s vs %2$s"),

        /**
         * The APK's digest does not match the digest contained in the APK Signature Scheme v2
         * signature.
         *
         * <ul>
         * <li>Parameter 1: content digest algorithm ({@link ContentDigestAlgorithm})</li>
         * <li>Parameter 2: hex-encoded expected digest of the APK ({@code String})</li>
         * <li>Parameter 3: hex-encoded actual digest of the APK ({@code String})</li>
         * </ul>
         */
        V2_SIG_APK_DIGEST_DID_NOT_VERIFY(
                "APK integrity check failed. %1$s digest mismatch."
                        + " Expected: <%2$s>, actual: <%3$s>"),

        /**
         * Failed to parse the list of signers contained in the APK Signature Scheme v3 signature.
         */
        V3_SIG_MALFORMED_SIGNERS("Malformed list of signers"),

        /**
         * Failed to parse this signer's signer block contained in the APK Signature Scheme v3
         * signature.
         */
        V3_SIG_MALFORMED_SIGNER("Malformed signer block"),

        /**
         * Public key embedded in the APK Signature Scheme v3 signature of this signer could not be
         * parsed.
         *
         * <ul>
         * <li>Parameter 1: error details ({@code Throwable})</li>
         * </ul>
         */
        V3_SIG_MALFORMED_PUBLIC_KEY("Malformed public key: %1$s"),

        /**
         * This APK Signature Scheme v3 signer's certificate could not be parsed.
         *
         * <ul>
         * <li>Parameter 1: index ({@code 0}-based) of the certificate in the signer's list of
         *     certificates ({@code Integer})</li>
         * <li>Parameter 2: sequence number ({@code 1}-based) of the certificate in the signer's
         *     list of certificates ({@code Integer})</li>
         * <li>Parameter 3: error details ({@code Throwable})</li>
         * </ul>
         */
        V3_SIG_MALFORMED_CERTIFICATE("Malformed certificate #%2$d: %3$s"),

        /**
         * Failed to parse this signer's signature record contained in the APK Signature Scheme v3
         * signature.
         *
         * <ul>
         * <li>Parameter 1: record number (first record is {@code 1}) ({@code Integer})</li>
         * </ul>
         */
        V3_SIG_MALFORMED_SIGNATURE("Malformed APK Signature Scheme v3 signature record #%1$d"),

        /**
         * Failed to parse this signer's digest record contained in the APK Signature Scheme v3
         * signature.
         *
         * <ul>
         * <li>Parameter 1: record number (first record is {@code 1}) ({@code Integer})</li>
         * </ul>
         */
        V3_SIG_MALFORMED_DIGEST("Malformed APK Signature Scheme v3 digest record #%1$d"),

        /**
         * This APK Signature Scheme v3 signer contains a malformed additional attribute.
         *
         * <ul>
         * <li>Parameter 1: attribute number (first attribute is {@code 1}) {@code Integer})</li>
         * </ul>
         */
        V3_SIG_MALFORMED_ADDITIONAL_ATTRIBUTE("Malformed additional attribute #%1$d"),

        /**
         * APK Signature Scheme v3 signature contains no signers.
         */
        V3_SIG_NO_SIGNERS("No signers in APK Signature Scheme v3 signature"),

        /**
         * APK Signature Scheme v3 signature contains multiple signers (only one allowed per
         * platform version).
         */
        V3_SIG_MULTIPLE_SIGNERS("Multiple APK Signature Scheme v3 signatures found for a single "
                + " platform version."),

        /**
         * APK Signature Scheme v3 signature found, but multiple v1 and/or multiple v2 signers
         * found, where only one may be used with APK Signature Scheme v3
         */
        V3_SIG_MULTIPLE_PAST_SIGNERS("Multiple signatures found for pre-v3 signing with an APK "
                + " Signature Scheme v3 signer.  Only one allowed."),

        /**
         * APK Signature Scheme v3 signature found, but its signer doesn't match the v1/v2 signers,
         * or have them as the root of its signing certificate history
         */
        V3_SIG_PAST_SIGNERS_MISMATCH(
                "v3 signer differs from v1/v2 signer without proper signing certificate lineage."),

        /**
         * This APK Signature Scheme v3 signer contains a signature produced using an unknown
         * algorithm.
         *
         * <ul>
         * <li>Parameter 1: algorithm ID ({@code Integer})</li>
         * </ul>
         */
        V3_SIG_UNKNOWN_SIG_ALGORITHM("Unknown signature algorithm: %1$#x"),

        /**
         * This APK Signature Scheme v3 signer contains an unknown additional attribute.
         *
         * <ul>
         * <li>Parameter 1: attribute ID ({@code Integer})</li>
         * </ul>
         */
        V3_SIG_UNKNOWN_ADDITIONAL_ATTRIBUTE("Unknown additional attribute: ID %1$#x"),

        /**
         * An exception was encountered while verifying APK Signature Scheme v3 signature of this
         * signer.
         *
         * <ul>
         * <li>Parameter 1: signature algorithm ({@link SignatureAlgorithm})</li>
         * <li>Parameter 2: exception ({@code Throwable})</li>
         * </ul>
         */
        V3_SIG_VERIFY_EXCEPTION("Failed to verify %1$s signature: %2$s"),

        /**
         * The APK Signature Scheme v3 signer contained an invalid value for either min or max SDK
         * versions.
         *
         * <ul>
         * <li>Parameter 1: minSdkVersion ({@code Integer})
         * <li>Parameter 2: maxSdkVersion ({@code Integer})
         * </ul>
         */
        V3_SIG_INVALID_SDK_VERSIONS("Invalid SDK Version parameter(s) encountered in APK Signature "
                + "scheme v3 signature: minSdkVersion %1$s maxSdkVersion: %2$s"),

        /**
         * APK Signature Scheme v3 signature over this signer's signed-data block did not verify.
         *
         * <ul>
         * <li>Parameter 1: signature algorithm ({@link SignatureAlgorithm})</li>
         * </ul>
         */
        V3_SIG_DID_NOT_VERIFY("%1$s signature over signed-data did not verify"),

        /**
         * This APK Signature Scheme v3 signer offers no signatures.
         */
        V3_SIG_NO_SIGNATURES("No signatures"),

        /**
         * This APK Signature Scheme v3 signer offers signatures but none of them are supported.
         */
        V3_SIG_NO_SUPPORTED_SIGNATURES("No supported signatures"),

        /**
         * This APK Signature Scheme v3 signer offers no certificates.
         */
        V3_SIG_NO_CERTIFICATES("No certificates"),

        /**
         * This APK Signature Scheme v3 signer's minSdkVersion listed in the signer's signed data
         * does not match the minSdkVersion listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: minSdkVersion in signature record ({@code Integer}) </li>
         * <li>Parameter 2: minSdkVersion in signed data ({@code Integer}) </li>
         * </ul>
         */
        V3_MIN_SDK_VERSION_MISMATCH_BETWEEN_SIGNER_AND_SIGNED_DATA_RECORD(
                "minSdkVersion mismatch between signed data and signature record:"
                        + " <%1$s> vs <%2$s>"),

        /**
         * This APK Signature Scheme v3 signer's maxSdkVersion listed in the signer's signed data
         * does not match the maxSdkVersion listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: maxSdkVersion in signature record ({@code Integer}) </li>
         * <li>Parameter 2: maxSdkVersion in signed data ({@code Integer}) </li>
         * </ul>
         */
        V3_MAX_SDK_VERSION_MISMATCH_BETWEEN_SIGNER_AND_SIGNED_DATA_RECORD(
                "maxSdkVersion mismatch between signed data and signature record:"
                        + " <%1$s> vs <%2$s>"),

        /**
         * This APK Signature Scheme v3 signer's public key listed in the signer's certificate does
         * not match the public key listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: hex-encoded public key from certificate ({@code String})</li>
         * <li>Parameter 2: hex-encoded public key from signatures record ({@code String})</li>
         * </ul>
         */
        V3_SIG_PUBLIC_KEY_MISMATCH_BETWEEN_CERTIFICATE_AND_SIGNATURES_RECORD(
                "Public key mismatch between certificate and signature record: <%1$s> vs <%2$s>"),

        /**
         * This APK Signature Scheme v3 signer's signature algorithms listed in the signatures
         * record do not match the signature algorithms listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: signature algorithms from signatures record ({@code List<Integer>})</li>
         * <li>Parameter 2: signature algorithms from digests record ({@code List<Integer>})</li>
         * </ul>
         */
        V3_SIG_SIG_ALG_MISMATCH_BETWEEN_SIGNATURES_AND_DIGESTS_RECORDS(
                "Signature algorithms mismatch between signatures and digests records"
                        + ": %1$s vs %2$s"),

        /**
         * The APK's digest does not match the digest contained in the APK Signature Scheme v3
         * signature.
         *
         * <ul>
         * <li>Parameter 1: content digest algorithm ({@link ContentDigestAlgorithm})</li>
         * <li>Parameter 2: hex-encoded expected digest of the APK ({@code String})</li>
         * <li>Parameter 3: hex-encoded actual digest of the APK ({@code String})</li>
         * </ul>
         */
        V3_SIG_APK_DIGEST_DID_NOT_VERIFY(
                "APK integrity check failed. %1$s digest mismatch."
                        + " Expected: <%2$s>, actual: <%3$s>"),

        /**
         * The signer's SigningCertificateLineage attribute containd a proof-of-rotation record with
         * signature(s) that did not verify.
         */
        V3_SIG_POR_DID_NOT_VERIFY("SigningCertificateLineage attribute containd a proof-of-rotation"
                + " record with signature(s) that did not verify."),

        /**
         * Failed to parse the SigningCertificateLineage structure in the APK Signature Scheme v3
         * signature's additional attributes section.
         */
        V3_SIG_MALFORMED_LINEAGE("Failed to parse the SigningCertificateLineage structure in the "
                + "APK Signature Scheme v3 signature's additional attributes section."),

        /**
         * The APK's signing certificate does not match the terminal node in the provided
         * proof-of-rotation structure describing the signing certificate history
         */
        V3_SIG_POR_CERT_MISMATCH(
                "APK signing certificate differs from the associated certificate found in the "
                        + "signer's SigningCertificateLineage."),

        /**
         * The APK Signature Scheme v3 signers encountered do not offer a continuous set of
         * supported platform versions.  Either they overlap, resulting in potentially two
         * acceptable signers for a platform version, or there are holes which would create problems
         * in the event of platform version upgrades.
         */
        V3_INCONSISTENT_SDK_VERSIONS("APK Signature Scheme v3 signers supported min/max SDK "
                + "versions are not continuous."),

        /**
         * The APK Signature Scheme v3 signers don't cover all requested SDK versions.
         *
         *  <ul>
         * <li>Parameter 1: minSdkVersion ({@code Integer})
         * <li>Parameter 2: maxSdkVersion ({@code Integer})
         * </ul>
         */
        V3_MISSING_SDK_VERSIONS("APK Signature Scheme v3 signers supported min/max SDK "
                + "versions do not cover the entire desired range.  Found min:  %1$s max %2$s"),

        /**
         * The SigningCertificateLineages for different platform versions using APK Signature Scheme
         * v3 do not go together.  Specifically, each should be a subset of another, with the size
         * of each increasing as the platform level increases.
         */
        V3_INCONSISTENT_LINEAGES("SigningCertificateLineages targeting different platform versions"
                + " using APK Signature Scheme v3 are not all a part of the same overall lineage."),

        /**
         * APK Signing Block contains an unknown entry.
         *
         * <ul>
         * <li>Parameter 1: entry ID ({@code Integer})</li>
         * </ul>
         */
        APK_SIG_BLOCK_UNKNOWN_ENTRY_ID("APK Signing Block contains unknown entry: ID %1$#x");

        private final String mFormat;

        private Issue(String format) {
            mFormat = format;
        }

        /**
         * Returns the format string suitable for combining the parameters of this issue into a
         * readable string. See {@link java.util.Formatter} for format.
         */
        private String getFormat() {
            return mFormat;
        }
    }

    /**
     * {@link Issue} with associated parameters. {@link #toString()} produces a readable formatted
     * form.
     */
    public static class IssueWithParams {
        private final Issue mIssue;
        private final Object[] mParams;

        /**
         * Constructs a new {@code IssueWithParams} of the specified type and with provided
         * parameters.
         */
        public IssueWithParams(Issue issue, Object[] params) {
            mIssue = issue;
            mParams = params;
        }

        /**
         * Returns the type of this issue.
         */
        public Issue getIssue() {
            return mIssue;
        }

        /**
         * Returns the parameters of this issue.
         */
        public Object[] getParams() {
            return mParams.clone();
        }

        /**
         * Returns a readable form of this issue.
         */
        @Override
        public String toString() {
            return String.format(mIssue.getFormat(), mParams);
        }
    }

    /**
     * Wrapped around {@code byte[]} which ensures that {@code equals} and {@code hashCode} operate
     * on the contents of the arrays rather than on references.
     */
    private static class ByteArray {
        private final byte[] mArray;
        private final int mHashCode;

        private ByteArray(byte[] arr) {
            mArray = arr;
            mHashCode = Arrays.hashCode(mArray);
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ByteArray other = (ByteArray) obj;
            if (hashCode() != other.hashCode()) {
                return false;
            }
            if (!Arrays.equals(mArray, other.mArray)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Builder of {@link ApkVerifier} instances.
     *
     * <p>The resulting verifier by default checks whether the APK will verify on all platform
     * versions supported by the APK, as specified by {@code android:minSdkVersion} attributes in
     * the APK's {@code AndroidManifest.xml}. The range of platform versions can be customized using
     * {@link #setMinCheckedPlatformVersion(int)} and {@link #setMaxCheckedPlatformVersion(int)}.
     */
    public static class Builder {
        private final File mApkFile;
        private final DataSource mApkDataSource;

        private Integer mMinSdkVersion;
        private int mMaxSdkVersion = Integer.MAX_VALUE;

        /**
         * Constructs a new {@code Builder} for verifying the provided APK file.
         */
        public Builder(File apk) {
            if (apk == null) {
                throw new NullPointerException("apk == null");
            }
            mApkFile = apk;
            mApkDataSource = null;
        }

        /**
         * Constructs a new {@code Builder} for verifying the provided APK.
         */
        public Builder(DataSource apk) {
            if (apk == null) {
                throw new NullPointerException("apk == null");
            }
            mApkDataSource = apk;
            mApkFile = null;
        }

        /**
         * Sets the oldest Android platform version for which the APK is verified. APK verification
         * will confirm that the APK is expected to install successfully on all known Android
         * platforms starting from the platform version with the provided API Level. The upper end
         * of the platform versions range can be modified via
         * {@link #setMaxCheckedPlatformVersion(int)}.
         *
         * <p>This method is useful for overriding the default behavior which checks that the APK
         * will verify on all platform versions supported by the APK, as specified by
         * {@code android:minSdkVersion} attributes in the APK's {@code AndroidManifest.xml}.
         *
         * @param minSdkVersion API Level of the oldest platform for which to verify the APK
         *
         * @see #setMinCheckedPlatformVersion(int)
         */
        public Builder setMinCheckedPlatformVersion(int minSdkVersion) {
            mMinSdkVersion = minSdkVersion;
            return this;
        }

        /**
         * Sets the newest Android platform version for which the APK is verified. APK verification
         * will confirm that the APK is expected to install successfully on all platform versions
         * supported by the APK up until and including the provided version. The lower end
         * of the platform versions range can be modified via
         * {@link #setMinCheckedPlatformVersion(int)}.
         *
         * @param maxSdkVersion API Level of the newest platform for which to verify the APK
         *
         * @see #setMinCheckedPlatformVersion(int)
         */
        public Builder setMaxCheckedPlatformVersion(int maxSdkVersion) {
            mMaxSdkVersion = maxSdkVersion;
            return this;
        }

        /**
         * Returns an {@link ApkVerifier} initialized according to the configuration of this
         * builder.
         */
        public ApkVerifier build() {
            return new ApkVerifier(
                    mApkFile,
                    mApkDataSource,
                    mMinSdkVersion,
                    mMaxSdkVersion);
        }
    }
}
