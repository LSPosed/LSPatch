/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.build.apkzlib.zip;

/**
 * Type of data descriptor that an entry has. Data descriptors are used if the CRC and sizing data
 * is not known when the data is being written and cannot be placed in the file's local header. In
 * those cases, after the file data itself, a data descriptor is placed after the entry's contents.
 *
 * <p>While the zip specification says the data descriptor should be used but it is optional. We
 * record also whether the data descriptor contained the 4-byte signature at the start of the block
 * or not.
 */
public enum DataDescriptorType {
  /** The entry has no data descriptor. */
  NO_DATA_DESCRIPTOR(0),

  /** The entry has a data descriptor that does not contain a signature. */
  DATA_DESCRIPTOR_WITHOUT_SIGNATURE(12),

  /** The entry has a data descriptor that contains a signature. */
  DATA_DESCRIPTOR_WITH_SIGNATURE(16);

  /** The number of bytes the data descriptor spans. */
  public int size;

  /**
   * Creates a new data descriptor.
   *
   * @param size the number of bytes the data descriptor spans
   */
  DataDescriptorType(int size) {
    this.size = size;
  }
}
