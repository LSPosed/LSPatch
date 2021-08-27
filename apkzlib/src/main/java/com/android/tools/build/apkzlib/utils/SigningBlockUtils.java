/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.apkzlib.utils;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import com.android.apksig.apk.ApkSigningBlockNotFoundException;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.apk.ApkUtils.ApkSigningBlock;
import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.internal.util.Pair;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.apksig.zip.ZipFormatException;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.annotation.Nullable;

/** Generates and appends a new block to APK v2 Signature block. */
public final class SigningBlockUtils {

  private static final int MAGIC_NUM_BYTES = 16;
  private static final int BLOCK_LENGTH_NUM_BYTES = 8;
  static final int SIZE_OF_BLOCK_NUM_BYTES = 8;
  static final int BLOCK_ID_NUM_BYTES = 4;

  static final int ANDROID_COMMON_PAGE_ALIGNMENT_NUM_BYTES = 4096;
  static final int VERITY_PADDING_BLOCK_ID = 0x42726577;

  /**
   * Generates a new block with the given block value and block id, and appends it to the signing
   * block.
   *
   * @param signingBlock Block containing v2 signature and (optionally) padding block or null.
   * @param blockValue byte array containing block value of the new block or null.
   * @param blockId block id of the new block.
   * @return APK v2 block with signatures and the new block. If {@code blockValue} is null the
   *     {@code signingBlock} is returned without any modification. If {@code signingBlock} is null,
   *     a new signature block is created containing the new block and, optionally, padding block.
   */
  public static byte[] addToSigningBlock(byte[] signingBlock, byte[] blockValue, int blockId)
      throws IOException {
    if (blockValue == null || blockValue.length == 0) {
      return signingBlock;
    }
    if (signingBlock == null || signingBlock.length == 0) {
      return createSigningBlock(blockValue, blockId);
    }
    return appendToSigningBlock(signingBlock, blockValue, blockId);
  }

  /**
   * Adds a new block to the signature block and a padding block, if required.
   *
   * @param signingBlock APK v2 signing block containing : length prefix, signers (can include
   *     padding block), length postfix and APK sig v2 block magic.
   * @param blockValue byte array containing block value of the new block.
   * @param blockId block id of the new block.
   * @return APK v2 signing block containing : length prefix, signers including the new block (may
   *     include padding block as well), length postfix and APK sig v2 block magic.
   */
  private static byte[] appendToSigningBlock(byte[] signingBlock, byte[] blockValue, int blockId)
      throws IOException {
    ImmutableList<Pair<byte[], Integer>> entries =
        ImmutableList.<Pair<byte[], Integer>>builder()
            .addAll(extractAllSigners(DataSources.asDataSource(ByteBuffer.wrap(signingBlock))))
            .add(Pair.of(blockValue, blockId))
            .build();
    return ApkSigningBlockUtils.generateApkSigningBlock(entries);
  }

  /**
   * Generate APK sig v2 block containing a block composed of the provided block value and id, and
   * (optionally) padding block.
   */
  private static byte[] createSigningBlock(byte[] blockValue, int blockId) {
    return ApkSigningBlockUtils.generateApkSigningBlock(
        ImmutableList.of(Pair.of(blockValue, blockId)));
  }

  /**
   * Extracts all signing block entries except padding block.
   *
   * @param signingBlock APK v2 signing block containing: length prefix, signers (can include
   *     padding block), length postfix and APK sig v2 block magic.
   * @return list of block entry value and block entry id pairs.
   */
  private static ImmutableList<Pair<byte[], Integer>> extractAllSigners(DataSource signingBlock)
      throws IOException {
    long wholeBlockSize = signingBlock.size();
    // Take the segment of the existing signing block without the length prefix (8 bytes)
    // at the beginning and the length and magic (24 bytes) at the end, so it is just the sequence
    // of length prefix id value pairs.
    DataSource lengthPrefixedIdValuePairsSource =
        signingBlock.slice(
            SIZE_OF_BLOCK_NUM_BYTES,
            wholeBlockSize - 2 * SIZE_OF_BLOCK_NUM_BYTES - MAGIC_NUM_BYTES);
    final int lengthAndIdByteCount = BLOCK_LENGTH_NUM_BYTES + BLOCK_ID_NUM_BYTES;
    ByteBuffer lengthAndId = ByteBuffer.allocate(lengthAndIdByteCount).order(LITTLE_ENDIAN);
    ImmutableList.Builder<Pair<byte[], Integer>> idValuePairs = ImmutableList.builder();

    for (int index = 0; index <= lengthPrefixedIdValuePairsSource.size() - lengthAndIdByteCount; ) {
      lengthPrefixedIdValuePairsSource.copyTo(index, lengthAndIdByteCount, lengthAndId);
      lengthAndId.flip();
      int blockLength = Ints.checkedCast(lengthAndId.getLong());
      int id = lengthAndId.getInt();
      lengthAndId.clear();

      if (id != VERITY_PADDING_BLOCK_ID) {
        int blockValueSize = blockLength - BLOCK_ID_NUM_BYTES;
        ByteBuffer blockValue = ByteBuffer.allocate(blockValueSize);
        lengthPrefixedIdValuePairsSource.copyTo(
            index + BLOCK_LENGTH_NUM_BYTES + BLOCK_ID_NUM_BYTES, blockValueSize, blockValue);
        idValuePairs.add(Pair.of(blockValue.array(), id));
      }

      index += blockLength + BLOCK_LENGTH_NUM_BYTES;
    }
    return idValuePairs.build();
  }

  /**
   * Extract a block with the given id from the APK. If there is more than one block with the same
   * ID, the first block will be returned. If there are no block with the give id, {@code null} will
   * be returned.
   *
   * @param apk APK file
   * @param blockId id of the block to be extracted.
   */
  @Nullable
  public static ByteBuffer extractBlock(File apk, int blockId)
      throws IOException, ZipFormatException, ApkSigningBlockNotFoundException {
    try (RandomAccessFile file = new RandomAccessFile(apk, "r")) {
      DataSource apkDataSource = DataSources.asDataSource(file);
      ApkSigningBlock signingBlockInfo =
          ApkUtils.findApkSigningBlock(apkDataSource, ApkUtils.findZipSections(apkDataSource));

      DataSource wholeV2Block = signingBlockInfo.getContents();
      final int lengthAndIdByteCount = BLOCK_LENGTH_NUM_BYTES + BLOCK_ID_NUM_BYTES;
      DataSource signingBlock =
          wholeV2Block.slice(
              SIZE_OF_BLOCK_NUM_BYTES,
              wholeV2Block.size() - SIZE_OF_BLOCK_NUM_BYTES - MAGIC_NUM_BYTES);
      ByteBuffer lengthAndId =
          ByteBuffer.allocate(lengthAndIdByteCount).order(ByteOrder.LITTLE_ENDIAN);
      for (int index = 0; index <= signingBlock.size() - lengthAndIdByteCount; ) {
        signingBlock.copyTo(index, lengthAndIdByteCount, lengthAndId);
        lengthAndId.flip();
        int blockLength = (int) lengthAndId.getLong();
        int id = lengthAndId.getInt();
        lengthAndId.flip();
        if (id == blockId) {
          ByteBuffer block = ByteBuffer.allocate(blockLength - BLOCK_ID_NUM_BYTES);
          signingBlock.copyTo(
              index + lengthAndIdByteCount, blockLength - BLOCK_ID_NUM_BYTES, block);
          block.flip();
          return block;
        }
        index += blockLength + BLOCK_LENGTH_NUM_BYTES;
      }
      return null;
    }
  }

  private SigningBlockUtils() {}
}
