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

import com.android.tools.build.apkzlib.utils.CachedSupplier;
import com.android.tools.build.apkzlib.utils.IOExceptionRunnable;
import com.android.tools.build.apkzlib.utils.IOExceptionWrapper;
import com.android.tools.build.apkzlib.zfile.ManifestAttributes;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileExtension;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import javax.annotation.Nullable;

/**
 * Extension to {@link ZFile} that will generate a manifest. The extension will register
 * automatically with the {@link ZFile}.
 *
 * <p>Creating this extension will ensure a manifest for the zip exists. This extension will
 * generate a manifest if one does not exist and will update an existing manifest, if one does
 * exist. The extension will also provide access to the manifest so that others may update the
 * manifest.
 *
 * <p>Apart from standard manifest elements, this extension does not handle any particular manifest
 * features such as signing or adding custom attributes. It simply generates a plain manifest and
 * provides infrastructure so that other extensions can add data in the manifest.
 *
 * <p>The manifest itself will only be written when the {@link ZFileExtension#beforeUpdate()}
 * notification is received, meaning all manifest manipulation is done in-memory.
 */
public class ManifestGenerationExtension {

  /** Name of META-INF directory. */
  private static final String META_INF_DIR = "META-INF";

  /** Name of the manifest file. */
  static final String MANIFEST_NAME = META_INF_DIR + "/MANIFEST.MF";

  /** Who should be reported as the manifest builder. */
  private final String builtBy;

  /** Who should be reported as the manifest creator. */
  private final String createdBy;

  /** The file this extension is attached to. {@code null} if not yet registered. */
  @Nullable private ZFile zFile;

  /** The zip file's manifest. */
  private final Manifest manifest;

  /**
   * Byte representation of the manifest. There is no guarantee that two writes of the java's {@code
   * Manifest} object will yield the same byte array (there is no guaranteed order of entries in the
   * manifest).
   *
   * <p>Because we need the byte representation of the manifest to be stable if there are no changes
   * to the manifest, we cannot rely on {@code Manifest} to generate the byte representation every
   * time we need the byte representation.
   *
   * <p>This cache will ensure that we will request one byte generation from the {@code Manifest}
   * and will cache it. All further requests of the manifest's byte representation will receive the
   * same byte array.
   */
  private final CachedSupplier<byte[]> manifestBytes;

  /**
   * Has the current manifest been changed and not yet flushed? If {@link #dirty} is {@code true},
   * then {@link #manifestBytes} should not be valid. This means that marking the manifest as dirty
   * should also invalidate {@link #manifestBytes}. To avoid breaking the invariant, instead of
   * setting {@link #dirty}, {@link #markDirty()} should be called.
   */
  private boolean dirty;

  /** The extension to register with the {@link ZFile}. {@code null} if not registered. */
  @Nullable private ZFileExtension extension;

  /**
   * Creates a new extension. This will not register the extension with the provided {@link ZFile}.
   * Until {@link #register(ZFile)} is invoked, this extension is not used.
   *
   * @param builtBy who built the manifest?
   * @param createdBy who created the manifest?
   */
  public ManifestGenerationExtension(String builtBy, String createdBy) {
    this.builtBy = builtBy;
    this.createdBy = createdBy;
    manifest = new Manifest();
    dirty = false;
    manifestBytes =
        new CachedSupplier<>(
            () -> {
              ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
              try {
                manifest.write(outBytes);
              } catch (IOException e) {
                throw new IOExceptionWrapper(e);
              }

              return outBytes.toByteArray();
            });
  }

  /**
   * Marks the manifest as being dirty, <i>i.e.</i>, its data has changed since it was last read
   * and/or written.
   */
  private void markDirty() {
    dirty = true;
    manifestBytes.reset();
  }

  /**
   * Registers the extension with the {@link ZFile} provided in the constructor.
   *
   * @param zFile the zip file to add the extension to
   * @throws IOException failed to analyze the zip
   */
  public void register(ZFile zFile) throws IOException {
    Preconditions.checkState(extension == null, "register() has already been invoked.");
    this.zFile = zFile;

    rebuildManifest();

    extension =
        new ZFileExtension() {
          @Nullable
          @Override
          public IOExceptionRunnable beforeUpdate() {
            return ManifestGenerationExtension.this::updateManifest;
          }
        };

    this.zFile.addZFileExtension(extension);
  }

  /** Rebuilds the zip file's manifest, if it needs changes. */
  private void rebuildManifest() throws IOException {
    Verify.verifyNotNull(zFile, "zFile == null");

    StoredEntry manifestEntry = zFile.get(MANIFEST_NAME);

    if (manifestEntry != null) {
      /*
       * Read the manifest entry in the zip file. Make sure we store these byte sequence
       * because writing the manifest may not generate the same byte sequence, which may
       * trigger an unnecessary re-sign of the jar.
       */
      manifest.clear();
      byte[] manifestBytes = manifestEntry.read();
      manifest.read(new ByteArrayInputStream(manifestBytes));
      this.manifestBytes.precomputed(manifestBytes);
    }

    Attributes mainAttributes = manifest.getMainAttributes();
    String currentVersion = mainAttributes.getValue(ManifestAttributes.MANIFEST_VERSION);
    if (currentVersion == null) {
      setMainAttribute(
          ManifestAttributes.MANIFEST_VERSION, ManifestAttributes.CURRENT_MANIFEST_VERSION);
    } else {
      if (!currentVersion.equals(ManifestAttributes.CURRENT_MANIFEST_VERSION)) {
        throw new IOException("Unsupported manifest version: " + currentVersion + ".");
      }
    }

    /*
     * We "blindly" override all other main attributes.
     */
    setMainAttribute(ManifestAttributes.BUILT_BY, builtBy);
    setMainAttribute(ManifestAttributes.CREATED_BY, createdBy);
  }

  /**
   * Sets the value of a main attribute.
   *
   * @param attribute the attribute
   * @param value the value
   */
  private void setMainAttribute(String attribute, String value) {
    Attributes mainAttributes = manifest.getMainAttributes();
    String current = mainAttributes.getValue(attribute);
    if (!value.equals(current)) {
      mainAttributes.putValue(attribute, value);
      markDirty();
    }
  }

  /**
   * Updates the manifest in the zip file, if it has been changed.
   *
   * @throws IOException failed to update the manifest
   */
  private void updateManifest() throws IOException {
    Verify.verifyNotNull(zFile, "zFile == null");

    if (!dirty) {
      return;
    }

    zFile.add(MANIFEST_NAME, new ByteArrayInputStream(manifestBytes.get()));
    dirty = false;
  }
}
