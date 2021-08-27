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

package com.android.tools.build.apkzlib.zip;

import com.android.tools.build.apkzlib.zip.utils.LittleEndianUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Contains the special purpose data for the Zip64 EOCD record.
 *
 * <p>According to the zip specification, the Zip64 EOCD is composed of a sequence of zero or more
 * Special Purpose Data fields. This class provides a way to access, parse, and modify that
 * information.
 *
 * <p>Each Special Purpose Data is represented by an instance of {@link Z64SpecialPurposeData} and
 * contains a header ID and data.
 */
public class Zip64ExtensibleDataSector {

  /**
   * The extensible data sector's raw data, if it is known. Either this variable or {@link #fields}
   * must be non-{@code null}.
   */
  @Nullable private final byte[] rawData;

  /**
   * The list of fields in the data sector. Will be populated if the data sector is created based on
   * a list of special purpose data; will also be populated after parsing if the Data Sector is
   * created based on the raw bytes.
   */
  @Nullable private ImmutableList<Z64SpecialPurposeData> fields;

  /**
   * Creates a Zip64 Extensible Data Sector based on existing raw data.
   *
   * @param rawData the raw data; will only be parsed if needed.
   */
  public Zip64ExtensibleDataSector(byte[] rawData) {
    this.rawData = rawData;
    fields = null;
  }

  /**
   * Creates an Extensible Data Sector with no special purpose data.
   */
  public Zip64ExtensibleDataSector() {
    rawData = null;
    fields = ImmutableList.of();
  }

  /**
   * Creates a Zip64 Extensible Data with the given Special purpose data.
   *
   * @param fields all special purpose data.
   */
  public Zip64ExtensibleDataSector(ImmutableList<Z64SpecialPurposeData> fields) {
    rawData = null;
    this.fields = fields;
  }

  int size() {
    if (rawData != null) {
      return rawData.length;
    } else {
      Preconditions.checkNotNull(fields);
      int sumSizes = 0;
      for (Z64SpecialPurposeData data : fields){
        sumSizes += data.size();
      }
      return sumSizes;
    }
  }

  void write(ByteBuffer out) throws IOException {
    if (rawData != null) {
      out.put(rawData);
    } else {
      Preconditions.checkNotNull(fields);
      for (Z64SpecialPurposeData data : fields) {
        data.write(out);
      }
    }
  }

  public ImmutableList<Z64SpecialPurposeData> getFields() throws IOException {
    if (fields == null) {
      parseData();
    }

    Preconditions.checkNotNull(fields);
    return fields;
  }

  private void parseData() throws IOException {
    Preconditions.checkNotNull(rawData);
    Preconditions.checkState(fields == null);

    List<Z64SpecialPurposeData> fields = new ArrayList<>();
    ByteBuffer buffer = ByteBuffer.wrap(rawData);

    while (buffer.remaining() > 0) {
      int headerId = LittleEndianUtils.readUnsigned2Le(buffer);
      long dataSize = LittleEndianUtils.readUnsigned4Le(buffer);

      byte[] data = new byte[Ints.checkedCast(dataSize)];
      if (dataSize < 0) {
        throw new IOException(
            "Invalid data size for special purpose data with header ID "
                + headerId
                + ": "
                + dataSize);
      }
      buffer.get(data);

      SpecialPurposeDataFactory factory = RawSpecialPurposeData::new;
      Z64SpecialPurposeData spd = factory.make(headerId, data);
      fields.add(spd);
    }
    this.fields = ImmutableList.copyOf(fields);
  }

  public interface Z64SpecialPurposeData {

    /** Length of header id and the size length fields that comes before the data */
    int PREFIX_LENGTH = 6;

    /**
     * Obtains the Special purpose data's header id.
     *
     * @return the data's header id.
     */
    int getHeaderId();

    /**
     * Obtains the size of the data in this special purpose data.
     *
     * @return the number of bytes needed to write the data.
     */
    int size();

    /**
     * Writes the special purpose data to the buffer.
     *
     * @param out the buffer where to write the data to; exactly {@link #size()} bytes will be
     *     written.
     * @throws IOException failed to write special purpose data.
     */
    void write(ByteBuffer out) throws IOException;
  }

  public  interface SpecialPurposeDataFactory {

    /**
     * Creates a new special purpose data.
     *
     * @param headerId the header ID
     * @param data the data in the special purpose data
     * @return the created special purpose data.
     * @throws IOException failed to create the special purpose data from the data given.
     */
    Z64SpecialPurposeData make(int headerId, byte[] data) throws IOException;
  }

  /**
   * Special Purpose Data containing raw data: this class represents a general "special purpose
   * data" containing an array of bytes as data.
   */
  public static class RawSpecialPurposeData implements Z64SpecialPurposeData {

    /** Header ID. */
    private final int headerId;

    /** Data in the segment */
    private final byte[] data;

    RawSpecialPurposeData(int headerId, byte[] data) {
      this.headerId = headerId;
      this.data = data;
    }

    @Override
    public int getHeaderId() {
      return headerId;
    }

    @Override
    public int size() {
      return PREFIX_LENGTH + data.length;
    }

    @Override
    public void write(ByteBuffer out) throws IOException {
      LittleEndianUtils.writeUnsigned2Le(out, headerId);
      LittleEndianUtils.writeUnsigned4Le(out, data.length);
      out.put(data);
    }
  }
}
