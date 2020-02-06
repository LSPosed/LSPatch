/*
 * Copyright (C) 2010 Ken Ellinwood
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
package net.fornwall.apksigner.zipio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.util.Date;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public final class ZioEntry implements Cloneable {

	private ZipInput zipInput;

	// public int signature = 0x02014b50;
	private short versionMadeBy;
	private short versionRequired;
	private short generalPurposeBits;
	private short compression;
	private short modificationTime;
	private short modificationDate;
	private int crc32;
	private int compressedSize;
	private int size;
	private String filename;
	private byte[] extraData;
	private short numAlignBytes = 0;
	private String fileComment;
	private short diskNumberStart;
	private short internalAttributes;
	private int externalAttributes;

	private int localHeaderOffset;
	private long dataPosition = -1;
	private byte[] data = null;
	private ZioEntryOutputStream entryOut = null;

	private static byte[] alignBytes = new byte[4];

	public ZioEntry(ZipInput input) throws IOException {
		this.zipInput = input;

		// 0 4 Central directory header signature = 0x02014b50
		int signature = input.readInt();
		if (signature != 0x02014b50) {
			// back up to the signature
			input.seek(input.getFilePointer() - 4);
			throw new IOException("Central directory header signature not found");
		}

		// 4 2 Version needed to extract (minimum)
		versionMadeBy = input.readShort();

		// 4 2 Version required
		versionRequired = input.readShort();

		// 6 2 General purpose bit flag
		generalPurposeBits = input.readShort();
		// Bits 1, 2, 3, and 11 are allowed to be set (first bit is bit zero). Any others are a problem.
		if ((generalPurposeBits & 0xF7F1) != 0x0000) {
			throw new IllegalStateException("Can't handle general purpose bits == "
					+ String.format("0x%04x", generalPurposeBits));
		}

		// 8 2 Compression method
		compression = input.readShort();

		// 10 2 File last modification time
		modificationTime = input.readShort();

		// 12 2 File last modification date
		modificationDate = input.readShort();

		// 14 4 CRC-32
		crc32 = input.readInt();

		// 18 4 Compressed size
		compressedSize = input.readInt();

		// 22 4 Uncompressed size
		size = input.readInt();

		// 26 2 File name length (n)
		short fileNameLen = input.readShort();
		// log.debug(String.format("File name length: 0x%04x", fileNameLen));

		// 28 2 Extra field length (m)
		short extraLen = input.readShort();
		// log.debug(String.format("Extra length: 0x%04x", extraLen));

		short fileCommentLen = input.readShort();
		diskNumberStart = input.readShort();
		internalAttributes = input.readShort();
		externalAttributes = input.readInt();
		localHeaderOffset = input.readInt();

		// 30 n File name
		filename = input.readString(fileNameLen);
		extraData = input.readBytes(extraLen);
		fileComment = input.readString(fileCommentLen);

		generalPurposeBits = (short) (generalPurposeBits & 0x0800); // Don't
																	// write a
																	// data
																	// descriptor,
																	// preserve
																	// UTF-8
																	// encoded
																	// filename
																	// bit

		// Don't write zero-length entries with compression.
		if (size == 0) {
			compressedSize = 0;
			compression = 0;
			crc32 = 0;
		}
	}

	public ZioEntry(String name) {
		filename = name;
		fileComment = "";
		compression = 8;
		extraData = new byte[0];
		setTime(System.currentTimeMillis());
	}

	public void readLocalHeader() throws IOException {
		ZipInput input = zipInput;
		input.seek(localHeaderOffset);

		// 0 4 Local file header signature = 0x04034b50
		int signature = input.readInt();
		if (signature != 0x04034b50) {
			throw new IllegalStateException(String.format("Local header not found at pos=0x%08x, file=%s",
					input.getFilePointer(), filename));
		}

		// This method is usually called just before the data read, so
		// its only purpose currently is to position the file pointer
		// for the data read. The entry's attributes might also have
		// been changed since the central dir entry was read (e.g.,
		// filename), so throw away the values here.

		// 4 2 Version needed to extract (minimum)
		/* versionRequired */input.readShort();

		// 6 2 General purpose bit flag
		/* generalPurposeBits */input.readShort();

		// 8 2 Compression method
		/* compression */input.readShort();

		// 10 2 File last modification time
		/* modificationTime */input.readShort();

		// 12 2 File last modification date
		/* modificationDate */input.readShort();

		// 14 4 CRC-32
		/* crc32 */input.readInt();

		// 18 4 Compressed size
		/* compressedSize */input.readInt();

		// 22 4 Uncompressed size
		/* size */input.readInt();

		// 26 2 File name length (n)
		short fileNameLen = input.readShort();

		// 28 2 Extra field length (m)
		short extraLen = input.readShort();

		// 30 n File name
		/* String localFilename = */input.readString(fileNameLen);

		// Extra data. FIXME: Avoid useless memory allocation.
		/* byte[] extra = */input.readBytes(extraLen);

		// Record the file position of this entry's data.
		dataPosition = input.getFilePointer();
	}

	public void writeLocalEntry(ZipOutput output) throws IOException {
		if (data == null && dataPosition < 0 && zipInput != null)
			readLocalHeader();
		localHeaderOffset = output.getFilePointer();

		if (entryOut != null) {
			entryOut.close();
			size = entryOut.getSize();
			data = ((ByteArrayOutputStream) entryOut.wrapped).toByteArray();
			compressedSize = data.length;
			crc32 = entryOut.getCRC();
		}

		output.writeInt(0x04034b50);
		output.writeShort(versionRequired);
		output.writeShort(generalPurposeBits);
		output.writeShort(compression);
		output.writeShort(modificationTime);
		output.writeShort(modificationDate);
		output.writeInt(crc32);
		output.writeInt(compressedSize);
		output.writeInt(size);
		output.writeShort((short) filename.length());

		numAlignBytes = 0;

		// Zipalign if the file is uncompressed, i.e., "Stored", and file size is not zero.
		if (compression == 0) {
			long dataPos = output.getFilePointer() + // current position
					2 + // plus size of extra data length
					filename.length() + // plus filename
					extraData.length; // plus extra data
			short dataPosMod4 = (short) (dataPos % 4);
			if (dataPosMod4 > 0) {
				numAlignBytes = (short) (4 - dataPosMod4);
			}
		}

		// 28 2 Extra field length (m)
		output.writeShort((short) (extraData.length + numAlignBytes));

		// 30 n File name
		output.writeString(filename);

		// Extra data
		output.writeBytes(extraData);

		// Zipalign bytes
		if (numAlignBytes > 0)
			output.writeBytes(alignBytes, 0, numAlignBytes);

		if (data != null) {
			output.writeBytes(data);
		} else {
			zipInput.seek(dataPosition);

			int bufferSize = Math.min(compressedSize, 8096);
			byte[] buffer = new byte[bufferSize];
			long totalCount = 0;

			while (totalCount != compressedSize) {
				int numRead = zipInput.in.read(buffer, 0, (int) Math.min(compressedSize - totalCount, bufferSize));
				if (numRead > 0) {
					output.writeBytes(buffer, 0, numRead);
					// if (debug)
					// getLogger().debug(
					// String.format("Wrote %d bytes", numRead));
					totalCount += numRead;
				} else
					throw new IllegalStateException(String.format(
							"EOF reached while copying %s with %d bytes left to go", filename, compressedSize
									- totalCount));
			}
		}
	}

	/** Returns the entry's data. */
	public byte[] getData() throws IOException {
		if (data != null)
			return data;

		byte[] tmpdata = new byte[size];

		InputStream din = getInputStream();
		int count = 0;

		while (count != size) {
			int numRead = din.read(tmpdata, count, size - count);
			if (numRead < 0)
				throw new IllegalStateException(String.format("Read failed, expecting %d bytes, got %d instead", size,
						count));
			count += numRead;
		}
		return tmpdata;
	}

	// Returns an input stream for reading the entry's data.
	public InputStream getInputStream() throws IOException {
		if (entryOut != null) {
			entryOut.close();
			size = entryOut.getSize();
			data = ((ByteArrayOutputStream) entryOut.wrapped).toByteArray();
			compressedSize = data.length;
			crc32 = entryOut.getCRC();
			entryOut = null;
			InputStream rawis = new ByteArrayInputStream(data);
			if (compression == 0)
				return rawis;
			else {
				// Hacky, inflate using a sequence of input streams that returns
				// 1 byte more than the actual length of the data.
				// This extra dummy byte is required by InflaterInputStream when
				// the data doesn't have the header and crc fields (as it is in
				// zip files).
				return new InflaterInputStream(new SequenceInputStream(rawis, new ByteArrayInputStream(new byte[1])),
						new Inflater(true));
			}
		}

		ZioEntryInputStream dataStream;
		dataStream = new ZioEntryInputStream(this);
		if (compression != 0) {
			// Note: When using nowrap=true with Inflater it is also necessary to provide
			// an extra "dummy" byte as input. This is required by the ZLIB native library
			// in order to support certain optimizations.
			dataStream.setReturnDummyByte(true);
			return new InflaterInputStream(dataStream, new Inflater(true));
		} else
			return dataStream;
	}

	// Returns an output stream for writing an entry's data.
	public OutputStream getOutputStream() {
		entryOut = new ZioEntryOutputStream(compression, new ByteArrayOutputStream());
		return entryOut;
	}

	public void write(ZipOutput output) throws IOException {
		output.writeInt(0x02014b50);
		output.writeShort(versionMadeBy);
		output.writeShort(versionRequired);
		output.writeShort(generalPurposeBits);
		output.writeShort(compression);
		output.writeShort(modificationTime);
		output.writeShort(modificationDate);
		output.writeInt(crc32);
		output.writeInt(compressedSize);
		output.writeInt(size);
		output.writeShort((short) filename.length());
		output.writeShort((short) (extraData.length + numAlignBytes));
		output.writeShort((short) fileComment.length());
		output.writeShort(diskNumberStart);
		output.writeShort(internalAttributes);
		output.writeInt(externalAttributes);
		output.writeInt(localHeaderOffset);

		output.writeString(filename);
		output.writeBytes(extraData);
		if (numAlignBytes > 0)
			output.writeBytes(alignBytes, 0, numAlignBytes);
		output.writeString(fileComment);
	}

	/** Returns time in Java format. */
	public long getTime() {
		int year = ((modificationDate >> 9) & 0x007f) + 80;
		int month = ((modificationDate >> 5) & 0x000f) - 1;
		int day = modificationDate & 0x001f;
		int hour = (modificationTime >> 11) & 0x001f;
		int minute = (modificationTime >> 5) & 0x003f;
		int seconds = (modificationTime << 1) & 0x003e;
		Date d = new Date(year, month, day, hour, minute, seconds);
		return d.getTime();
	}

	/** Set the file timestamp (using a Java time value). */
	public void setTime(long time) {
		Date d = new Date(time);
		long dtime;
		int year = d.getYear() + 1900;
		if (year < 1980) {
			dtime = (1 << 21) | (1 << 16);
		} else {
			dtime = (year - 1980) << 25 | (d.getMonth() + 1) << 21 | d.getDate() << 16 | d.getHours() << 11
					| d.getMinutes() << 5 | d.getSeconds() >> 1;
		}

		modificationDate = (short) (dtime >> 16);
		modificationTime = (short) (dtime & 0xFFFF);
	}

	public boolean isDirectory() {
		return filename.endsWith("/");
	}

	public String getName() {
		return filename;
	}

	public void setName(String filename) {
		this.filename = filename;
	}

	/** Use 0 (STORED), or 8 (DEFLATE). */
	public void setCompression(int compression) {
		this.compression = (short) compression;
	}

	public short getVersionMadeBy() {
		return versionMadeBy;
	}

	public short getVersionRequired() {
		return versionRequired;
	}

	public short getGeneralPurposeBits() {
		return generalPurposeBits;
	}

	public short getCompression() {
		return compression;
	}

	public int getCrc32() {
		return crc32;
	}

	public int getCompressedSize() {
		return compressedSize;
	}

	public int getSize() {
		return size;
	}

	public byte[] getExtraData() {
		return extraData;
	}

	public String getFileComment() {
		return fileComment;
	}

	public short getDiskNumberStart() {
		return diskNumberStart;
	}

	public short getInternalAttributes() {
		return internalAttributes;
	}

	public int getExternalAttributes() {
		return externalAttributes;
	}

	public int getLocalHeaderOffset() {
		return localHeaderOffset;
	}

	public long getDataPosition() {
		return dataPosition;
	}

	public ZioEntryOutputStream getEntryOut() {
		return entryOut;
	}

	public ZipInput getZipInput() {
		return zipInput;
	}

}
