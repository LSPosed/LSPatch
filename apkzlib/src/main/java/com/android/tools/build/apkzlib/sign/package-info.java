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

/**
 * The {@code sign} package provides extensions for the {@code zip} package that allow:
 *
 * <ul>
 *   <li>Adding a {@code MANIFEST.MF} file to a zip making a jar.
 *   <li>Signing a jar.
 *   <li>Fully signing a jar using v2 apk signature.
 * </ul>
 *
 * <p>Because the {@code zip} package is completely independent of the {@code sign} package, the
 * actual coordination between the two is complex. The {@code sign} package works by registering
 * extensions with the {@code zip} package. These extensions are notified in changes made in the zip
 * and will change the zip file itself.
 *
 * <p>The {@link com.android.apkzlib.sign.ManifestGenerationExtension} extension will ensure the zip
 * has a manifest file and is, therefore, a valid jar. The {@link
 * com.android.apkzlib.sign.SigningExtension} extension will ensure the jar is signed.
 *
 * <p>The extension mechanism used is the one provided in the {@code zip} package (see {@link
 * com.android.apkzlib.zip.ZFile} and {@link com.android.apkzlib.zip.ZFileExtension}. Building the
 * zip and then operating the extensions is not done sequentially, as we don't want to build a zip
 * and then sign it. We want to build a zip that is automatically signed. Extension are basically
 * observers that register on the zip and are notified when things happen in the zip. They will then
 * modify the zip accordingly.
 *
 * <p>The zip file notifies extensions in 4 critical moments: when a file is added or removed from
 * the zip, when the zip is about to be flushed to disk and when the zip's entries have been flushed
 * but the central directory not. At these moments, the extensions can act to update the zip in any
 * way they need.
 *
 * <p>To see how this works, consider the manifest generation extension: when the extension is
 * created, it checks the zip file to see if there is a manifest. If a manifest exists and does not
 * need updating, it does not change anything, otherwise it generates a new manifest for the zip
 * file. At this point, the extension could write the manifest to the zip, but we opted not to. It
 * would be irrelevant anyway as the zip will only be written when flushed.
 *
 * <p>Now, when the {@code ZFile} notifies the extension that it is about to start writing the zip
 * file, the manifest extension, if it has noted that the manifest needs to be rewritten, will --
 * before the {@code ZFile} actually writes anything -- modify the zip and add or replace the
 * existing manifest file. So, process-wise, the zip is written only once with the correct manifest.
 * The flow is as follows (if only the manifest generation extension was added to the {@code
 * ZFile}):
 *
 * <ol>
 *   <li>{@code ZFile.update()} is called.
 *   <li>{@code ZFile} calls {@code beforeUpdate()} for all {@code ZFileExtensions} registered, in
 *       this case, only the instance of the anonymous inner class generated in the {@code
 *       ManifestGenerationExtension} constructor is invoked.
 *   <li>{@code ManifestGenerationExtension.updateManifest()} is called.
 *   <li>If the manifest does not need to be updated, {@code updateManifest()} returns immediately.
 *   <li>If the manifest needs updating, {@code ZFile.add()} is invoked to add or replace the
 *       manifest.
 *   <li>{@code ManifestGenerationExtension.updateManifest()} returns.
 *   <li>{@code ZFile.update()} continues and writes the zip file, containing the manifest.
 *   <li>The zip is finally written with an updated manifest.
 * </ol>
 *
 * <p>To generate a signed apk, we need to add a second extension, the {@code SigningExtension}.
 * This extension will also register listeners with the {@code ZFile}.
 *
 * <p>In this case the flow would be (starting a bit earlier for clarity and assuming a package task
 * in the build process):
 *
 * <ol>
 *   <li>Package task creates a {@code ZFile} on the target apk (or non-existing file, if there is
 *       no target apk in the output directory).
 *   <li>Package task configures the {@code ZFile} with alignment rules.
 *   <li>Package task creates a {@code ManifestGenerationExtension}.
 *   <li>Package task registers the {@code ManifestGenerationExtension} with the {@code ZFile}.
 *   <li>The {@code ManifestGenerationExtension} looks at the {@code ZFile} to see if there is valid
 *       manifest. No changes are done to the {@code ZFile}.
 *   <li>Package task creates a {@code SigningExtension}.
 *   <li>Package task registers the {@code SigningExtension} with the {@code ZFile}.
 *   <li>The {@code SigningExtension} registers a {@code ZFileExtension} with the {@code ZFile} and
 *       look at the {@code ZFile} to see if there is a valid signature file.
 *   <li>If there are changes to the digital signature file needed, these are marked internally in
 *       the extension. If there are changes needed to the digests, the manifest is updated (by
 *       calling {@code ManifestGenerationExtension}.<br>
 *       <em>(note that this point, the apk file, if any existed, has not been touched, the manifest
 *       is only updated in memory and the digests of all files in the apk, if any, have been
 *       computed and stored in memory only; the digital signature of the {@code SF} file has not
 *       been computed.) </em>
 *   <li>The Package task now adds all files to the {@code ZFile}.
 *   <li>For each file that is added (*), {@code ZFile} calls the added {@code ZFileExtension.added}
 *       method of all registered extensions.
 *   <li>The {@code ManifestGenerationExtension} ignores added invocations.
 *   <li>The {@code SigningExtension} computes the digest for the added file and stores them in the
 *       manifest.<br>
 *       <em>(when all files are added to the apk, all digests are computed and the manifest is
 *       updated but only in memory; the apk file has not been touched; also note that {@code ZFile}
 *       has not actually written anything to disk at this point, all files added are kept in
 *       memory).</em>
 *   <li>Package task calls {@code ZFile.update()} to update the apk.
 *   <li>{@code ZFile} calls {@code before()} for all {@code ZFileExtensions} registered. This is
 *       done before anything is written. In this case both the {@code ManifestGenerationExtension}
 *       and {@code SigningExtension} are invoked.
 *   <li>The {@code ManifestGenerationExtension} will update the {@code ZFile} with the new
 *       manifest, unless nothing has changed, in which case it does nothing.
 *   <li>The {@code SigningExtension} will add the SF file (unless nothing has changed), will
 *       compute the digital signature of the SF file and write it to the {@code ZFile}.<br>
 *       <em>(note that the order by which the {@code ManifestGenerationExtension} and {@code
 *       SigningExtension} are called is non-deterministic; however, this is not a problem because
 *       the manifest is already computed by the {@code ManifestGenerationExtension} at this time
 *       and the {@code SigningExtension} will obtain the manifest data from the {@code
 *       ManifestGenerationExtension} and not from the {@code ZFile}; this means that the {@code SF}
 *       file may be added to the {@code ZFile} before the {@code MF} file, but that is
 *       irrelevant.)</em>
 *   <li>Once both extensions have finished doing the {@code beforeUpdate()} method, the {@code
 *       ZFile.update()} method continues.
 *   <li>{@code ZFile.update()} writes all changes and new entries to the zip file.
 *   <li>{@code ZFile.update()} calls {@code ZFileExtension.entriesWritten()} for all registered
 *       extensions. {@code SigningExtension} will kick in at this point, if v2 signature has
 *       changed.
 *   <li>{@code ZFile} writes the central directory and EOCD.
 *   <li>{@code ZFile.update()} returns control to the package task.
 *   <li>The package task finishes.
 * </ol>
 *
 * <em>(*) There is a number of optimizations if we're adding files from another {@code ZFile},
 * which is the case when we add the output of aapt to the apk. In particular, files from the aapt
 * are ignored if they are already in the apk (same name, same CRC32) and also files copied from the
 * aapt's output are not recompressed (the binary compressed data is directly copied to the
 * zip).</em>
 *
 * <p>If there are no changes to the {@code ZFile} made by the package task and the file's manifest
 * and v1 signatures are correct, neither the {@code ManifestGenerationExtension} nor the {@code
 * SigningExtension} will not do anything on the {@code beforeUpdate()} and the {@code ZFile} won't
 * even be open for writing.
 *
 * <p>This implementation provides perfect incremental updates.
 *
 * <p>Additionally, by adding/removing extensions we can configure what type of apk we want:
 *
 * <ul>
 *   <li>No SigningExtension => Aligned, unsigned apk.
 *   <li>SigningExtension => Aligned, signed apk.
 * </ul>
 *
 * So, by configuring which extensions to add, the package task can decide what type of apk we want.
 */
package com.android.apkzlib.sign;
