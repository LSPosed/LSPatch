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

package com.android.apksigner;

import java.nio.ByteBuffer;

/**
 * Hexadecimal encoding where each byte is represented by two hexadecimal digits.
 */
class HexEncoding {

    /** Hidden constructor to prevent instantiation. */
    private HexEncoding() {}

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * Encodes the provided data as a hexadecimal string.
     */
    public static String encode(byte[] data, int offset, int length) {
        StringBuilder result = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            byte b = data[offset + i];
            result.append(HEX_DIGITS[(b >>> 4) & 0x0f]);
            result.append(HEX_DIGITS[b & 0x0f]);
        }
        return result.toString();
    }

    /**
     * Encodes the provided data as a hexadecimal string.
     */
    public static String encode(byte[] data) {
        return encode(data, 0, data.length);
    }

    /**
     * Encodes the remaining bytes of the provided {@link ByteBuffer} as a hexadecimal string.
     */
    public static String encodeRemaining(ByteBuffer data) {
        return encode(data.array(), data.arrayOffset() + data.position(), data.remaining());
    }
}
