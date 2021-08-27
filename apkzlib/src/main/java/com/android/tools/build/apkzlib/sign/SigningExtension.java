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

import com.android.apksig.ApkSignerEngine;
import com.android.apksig.ApkVerifier;
import com.android.apksig.DefaultApkSignerEngine;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.util.DataSink;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.tools.build.apkzlib.utils.IOExceptionRunnable;
import com.android.tools.build.apkzlib.utils.SigningBlockUtils;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileExtension;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * {@link ZFile} extension which signs the APK.
 *
 * <p>This extension is capable of signing the APK using JAR signing (aka v1 scheme) and APK
 * Signature Scheme v2 (aka v2 scheme). Which schemes are actually used is specified by parameters
 * to this extension's constructor.
 */
public class SigningExtension {
  private static final int MAX_READ_CHUNK_SIZE = 65536;

  // IMPLEMENTATION NOTE: Most of the heavy lifting is performed by the ApkSignerEngine primitive
  // from apksig library. This class is an adapter between ZFile extension and ApkSignerEngine.
  // This class takes care of invoking the right methods on ApkSignerEngine in response to ZFile
  // extension events/callbacks.
  //
  // The main issue leading to additional complexity in this class is that the current build
  // pipeline does not reuse ApkSignerEngine instances (or ZFile extension instances for that
  // matter) for incremental builds. Thus:
  // * ZFile extension receives no events for JAR entries already in the APK whereas
  //   ApkSignerEngine needs to know about all JAR entries to be covered by signature. Thus, this
  //   class, during "beforeUpdate" ZFile event, notifies ApkSignerEngine about JAR entries
  //   already in the APK which ApkSignerEngine hasn't yet been told about -- these are the JAR
  //   entries which the incremental build session did not touch.
  // * The build pipeline expects the APK not to change if no JAR entry was added to it or removed
  //   from it whereas ApkSignerEngine produces no output only if it has already produced a signed
  //   APK and no changes have since been made to it. This class addresses this issue by checking
  //   in its "register" method whether the APK is correctly signed and, only if that's the case,
  //   doesn't modify the APK unless a JAR entry is added to it or removed from it after
  //   "register".

  /** APK signer which performs most of the heavy lifting. */
  private final ApkSignerEngine signer;

  /** Names of APK entries which have been processed by {@link #signer}. */
  private final Set<String> signerProcessedOutputEntryNames = new HashSet<>();

  /** Signing block Id for SDK dependency block. */
  static final int DEPENDENCY_INFO_BLOCK_ID = 0x504b4453;

  /** SDK dependencies of the APK */
  @Nullable private byte[] sdkDependencyData;

  /**
   * Cached contents of the most recently output APK Signing Block or {@code null} if the block
   * hasn't yet been output.
   */
  @Nullable private byte[] cachedApkSigningBlock;

  /**
   * {@code true} if signatures may need to be output, {@code false} if there's no need to output
   * signatures. This is used in an optimization where we don't modify the APK if it's already
   * signed and if no JAR entries have been added to or removed from the file.
   */
  private boolean dirty;

  /** The extension registered with the {@link ZFile}. {@code null} if not registered. */
  @Nullable private ZFileExtension extension;

  /** The file this extension is attached to. {@code null} if not yet registered. */
  @Nullable private ZFile zFile;

  /** A buffer used to read data from entries to feed to digests */
  private final Supplier<byte[]> digestBuffer =
      Suppliers.memoize(() -> new byte[MAX_READ_CHUNK_SIZE]);

  /** An object that has all necessary information to sign the zip file and verify its signature */
  private final SigningOptions options;

  public SigningExtension(SigningOptions opts) throws InvalidKeyException {
    DefaultApkSignerEngine.SignerConfig signerConfig =
        new DefaultApkSignerEngine.SignerConfig.Builder(
                "CERT", opts.getKey(), opts.getCertificates())
            .build();
    signer =
        new DefaultApkSignerEngine.Builder(ImmutableList.of(signerConfig), opts.getMinSdkVersion())
            .setOtherSignersSignaturesPreserved(false)
            .setV1SigningEnabled(opts.isV1SigningEnabled())
            .setV2SigningEnabled(opts.isV2SigningEnabled())
            .setV3SigningEnabled(false)
            .setCreatedBy("1.0 (Android)")
            .build();
    if (opts.getSdkDependencyData() != null) {
      sdkDependencyData = opts.getSdkDependencyData();
    }
    if (opts.getExecutor() != null) {
      signer.setExecutor(opts.getExecutor());
    }
    this.options = opts;
  }

  public void register(ZFile zFile) throws NoSuchAlgorithmException, IOException {
    Preconditions.checkState(extension == null, "register() already invoked");
    this.zFile = zFile;
    switch (options.getValidation()) {
      case ALWAYS_VALIDATE:
        dirty = !isCurrentSignatureAsRequested();
        break;
      case ASSUME_VALID:
        if (options.isV1SigningEnabled()) {
          Set<String> entryNames =
              ImmutableSet.copyOf(
                  Iterables.transform(
                      zFile.entries(), e -> e.getCentralDirectoryHeader().getName()));
          StoredEntry manifestEntry = zFile.get(ManifestGenerationExtension.MANIFEST_NAME);

          Preconditions.checkNotNull(
              manifestEntry,
              "No manifest found in apk for incremental build with enabled v1 signature");
          signerProcessedOutputEntryNames.addAll(
              this.signer.initWith(manifestEntry.read(), entryNames));
        }

        dirty = false;
        break;
      case ASSUME_INVALID:
        dirty = true;
        break;
    }
    extension =
        new ZFileExtension() {
          @Override
          public IOExceptionRunnable added(StoredEntry entry, @Nullable StoredEntry replaced) {
            return () -> onZipEntryOutput(entry);
          }

          @Override
          public IOExceptionRunnable removed(StoredEntry entry) {
            String entryName = entry.getCentralDirectoryHeader().getName();
            return () -> onZipEntryRemovedFromOutput(entryName);
          }

          @Override
          public IOExceptionRunnable beforeUpdate() throws IOException {
            return () -> onOutputZipReadyForUpdate();
          }

          @Override
          public void entriesWritten() throws IOException {
            onOutputZipEntriesWritten();
          }

          @Override
          public void closed() {
            onOutputClosed();
          }
        };
    this.zFile.addZFileExtension(extension);
  }

  /**
   * Returns {@code true} if the APK's signatures are as requested by parameters to this signing
   * extension.
   */
  private boolean isCurrentSignatureAsRequested() throws IOException, NoSuchAlgorithmException {
    ApkVerifier.Result result;
    try {
      result =
          new ApkVerifier.Builder(zFile.asDataSource())
              .setMinCheckedPlatformVersion(options.getMinSdkVersion())
              .build()
              .verify();
    } catch (ApkFormatException e) {
      // Malformed APK
      return false;
    }

    if (!result.isVerified()) {
      // Signature(s) did not verify
      return false;
    }

    if ((result.isVerifiedUsingV1Scheme() != options.isV1SigningEnabled())
        || (result.isVerifiedUsingV2Scheme() != options.isV2SigningEnabled())) {
      // APK isn't signed with exactly the schemes we want it to be signed
      return false;
    }

    List<X509Certificate> verifiedSignerCerts = result.getSignerCertificates();
    if (verifiedSignerCerts.size() != 1) {
      // APK is not signed by exactly one signer
      return false;
    }

    byte[] expectedEncodedCert;
    byte[] actualEncodedCert;
    try {
      expectedEncodedCert = options.getCertificates().get(0).getEncoded();
      actualEncodedCert = verifiedSignerCerts.get(0).getEncoded();
    } catch (CertificateEncodingException e) {
      // Failed to encode signing certificates
      return false;
    }

    if (!Arrays.equals(expectedEncodedCert, actualEncodedCert)) {
      // APK is signed by a wrong signer
      return false;
    }

    // APK is signed the way we want it to be signed
    return true;
  }

  private void onZipEntryOutput(StoredEntry entry) throws IOException {
    setDirty();
    String entryName = entry.getCentralDirectoryHeader().getName();
    // This event may arrive after the entry has already been deleted. In that case, we don't
    // report the addition of the entry to ApkSignerEngine.
    if (entry.isDeleted()) {
      return;
    }
    ApkSignerEngine.InspectJarEntryRequest inspectEntryRequest = signer.outputJarEntry(entryName);
    signerProcessedOutputEntryNames.add(entryName);
    if (inspectEntryRequest != null) {
      try (InputStream inputStream = new BufferedInputStream(entry.open())) {
        copyStreamToDataSink(inputStream, inspectEntryRequest.getDataSink());
      }
      inspectEntryRequest.done();
    }
  }

  private void copyStreamToDataSink(InputStream inputStream, DataSink dataSink) throws IOException {
    int bytesRead;
    byte[] buffer = digestBuffer.get();
    while ((bytesRead = inputStream.read(buffer)) > 0) {
      dataSink.consume(buffer, 0, bytesRead);
    }
  }

  private void onZipEntryRemovedFromOutput(String entryName) {
    setDirty();
    signer.outputJarEntryRemoved(entryName);
    signerProcessedOutputEntryNames.remove(entryName);
  }

  private void onOutputZipReadyForUpdate() throws IOException {
    if (!dirty) {
      return;
    }

    // Notify signer engine about ZIP entries that have appeared in the output without the
    // engine knowing. Also identify ZIP entries which disappeared from the output without the
    // engine knowing.
    Set<String> unprocessedRemovedEntryNames = new HashSet<>(signerProcessedOutputEntryNames);
    for (StoredEntry entry : zFile.entries()) {
      String entryName = entry.getCentralDirectoryHeader().getName();
      unprocessedRemovedEntryNames.remove(entryName);
      if (!signerProcessedOutputEntryNames.contains(entryName)) {
        // Signer engine is not yet aware that this entry is in the output
        onZipEntryOutput(entry);
      }
    }

    // Notify signer engine about entries which disappeared from the output without the engine
    // knowing
    for (String entryName : unprocessedRemovedEntryNames) {
      onZipEntryRemovedFromOutput(entryName);
    }

    // Check whether we need to output additional JAR entries which comprise the v1 signature
    ApkSignerEngine.OutputJarSignatureRequest addV1SignatureRequest;
    try {
      addV1SignatureRequest = signer.outputJarEntries();
    } catch (Exception e) {
      throw new IOException("Failed to generate v1 signature", e);
    }
    if (addV1SignatureRequest == null) {
      return;
    }

    // We need to output additional JAR entries which comprise the v1 signature
    List<ApkSignerEngine.OutputJarSignatureRequest.JarEntry> v1SignatureEntries =
        new ArrayList<>(addV1SignatureRequest.getAdditionalJarEntries());

    // Reorder the JAR entries comprising the v1 signature so that MANIFEST.MF is the first
    // entry. This ensures that it cleanly overwrites the existing MANIFEST.MF output by
    // ManifestGenerationExtension.
    for (int i = 0; i < v1SignatureEntries.size(); i++) {
      ApkSignerEngine.OutputJarSignatureRequest.JarEntry entry = v1SignatureEntries.get(i);
      String name = entry.getName();
      if (!ManifestGenerationExtension.MANIFEST_NAME.equals(name)) {
        continue;
      }
      if (i != 0) {
        v1SignatureEntries.remove(i);
        v1SignatureEntries.add(0, entry);
      }
      break;
    }

    // Output the JAR entries comprising the v1 signature
    for (ApkSignerEngine.OutputJarSignatureRequest.JarEntry entry : v1SignatureEntries) {
      String name = entry.getName();
      byte[] data = entry.getData();
      zFile.add(name, new ByteArrayInputStream(data));
    }

    addV1SignatureRequest.done();
  }

  private void onOutputZipEntriesWritten() throws IOException {
    if (!dirty) {
      return;
    }

    // Check whether we should output an APK Signing Block which contains v2 signatures
    byte[] apkSigningBlock;
    byte[] centralDirBytes = zFile.getCentralDirectoryBytes();
    byte[] eocdBytes = zFile.getEocdBytes();
    ApkSignerEngine.OutputApkSigningBlockRequest2 addV2SignatureRequest;
    // This event may arrive a second time -- after we write out the APK Signing Block. Thus, we
    // cache the block to speed things up. The cached block is invalidated by any changes to the
    // file (as reported to this extension).
    if (cachedApkSigningBlock != null) {
      apkSigningBlock = cachedApkSigningBlock;
      addV2SignatureRequest = null;
    } else {
      DataSource centralDir = DataSources.asDataSource(ByteBuffer.wrap(centralDirBytes));
      DataSource eocd = DataSources.asDataSource(ByteBuffer.wrap(eocdBytes));
      long zipEntriesSizeBytes =
          zFile.getCentralDirectoryOffset() - zFile.getExtraDirectoryOffset();
      DataSource zipEntries = zFile.asDataSource(0, zipEntriesSizeBytes);
      try {
        addV2SignatureRequest = signer.outputZipSections2(zipEntries, centralDir, eocd);
      } catch (NoSuchAlgorithmException
          | InvalidKeyException
          | SignatureException
          | ApkFormatException
          | IOException e) {
        throw new IOException("Failed to generate v2 signature", e);
      }

      if (addV2SignatureRequest != null) {
        apkSigningBlock = addV2SignatureRequest.getApkSigningBlock();
        if (sdkDependencyData != null) {
          apkSigningBlock =
              SigningBlockUtils.addToSigningBlock(
                  apkSigningBlock, sdkDependencyData, DEPENDENCY_INFO_BLOCK_ID);
        }
        apkSigningBlock =
            Bytes.concat(
                new byte[addV2SignatureRequest.getPaddingSizeBeforeApkSigningBlock()],
                apkSigningBlock);
      } else {
        apkSigningBlock = new byte[0];
        if (sdkDependencyData != null) {
          apkSigningBlock =
              SigningBlockUtils.addToSigningBlock(
                  apkSigningBlock, sdkDependencyData, DEPENDENCY_INFO_BLOCK_ID);
          int paddingSize =
              ApkSigningBlockUtils.generateApkSigningBlockPadding(
                      zipEntries, /* apkSigningBlockPaddingSupported */ true)
                  .getSecond();
          apkSigningBlock = Bytes.concat(new byte[paddingSize], apkSigningBlock);
        }
      }
      cachedApkSigningBlock = apkSigningBlock;
    }

    // Insert the APK Signing Block into the output right before the ZIP Central Directory and
    // accordingly update the start offset of ZIP Central Directory in ZIP End of Central
    // Directory.
    zFile.directWrite(
        zFile.getCentralDirectoryOffset() - zFile.getExtraDirectoryOffset(), apkSigningBlock);
    zFile.setExtraDirectoryOffset(apkSigningBlock.length);

    if (addV2SignatureRequest != null) {
      addV2SignatureRequest.done();
    }
  }

  private void onOutputClosed() {
    if (!dirty) {
      return;
    }
    signer.outputDone();
    dirty = false;
  }

  private void setDirty() {
    dirty = true;
    cachedApkSigningBlock = null;
  }
}
