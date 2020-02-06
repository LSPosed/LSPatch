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

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public final class ZipOutput implements AutoCloseable {

	final OutputStream out;
	int filePointer = 0;
	final List<ZioEntry> entriesWritten = new LinkedList<>();
	final Set<String> namesWritten = new HashSet<>();

	public ZipOutput(OutputStream out) {
		this.out = out;
	}

	public void write(ZioEntry entry) throws IOException {
		String entryName = entry.getName();
		if (namesWritten.contains(entryName)) {
			System.err.println("Skipping duplicate file in output: " + entryName);
			return;
		}
		entry.writeLocalEntry(this);
		entriesWritten.add(entry);
		namesWritten.add(entryName);
	}

	public int getFilePointer() {
		return filePointer;
	}

	public void writeInt(int value) throws IOException {
		byte[] data = new byte[4];
		for (int i = 0; i < 4; i++) {
			data[i] = (byte) (value & 0xFF);
			value = value >> 8;
		}
		out.write(data);
		filePointer += 4;
	}

	public void writeShort(short value) throws IOException {
		byte[] data = new byte[2];
		for (int i = 0; i < 2; i++) {
			data[i] = (byte) (value & 0xFF);
			value = (short) (value >> 8);
		}
		out.write(data);
		filePointer += 2;
	}

	public void writeString(String value) throws IOException {
		byte[] data = value.getBytes();
		out.write(data);
		filePointer += data.length;
	}

	public void writeBytes(byte[] value) throws IOException {
		out.write(value);
		filePointer += value.length;
	}

	public void writeBytes(byte[] value, int offset, int length) throws IOException {
		out.write(value, offset, length);
		filePointer += length;
	}

	@Override
	public void close() throws IOException {
		CentralEnd centralEnd = new CentralEnd();
		centralEnd.centralStartOffset = getFilePointer();
		centralEnd.numCentralEntries = centralEnd.totalCentralEntries = (short) entriesWritten.size();

		for (ZioEntry entry : entriesWritten)
			entry.write(this);

		centralEnd.centralDirectorySize = (getFilePointer() - centralEnd.centralStartOffset);
		centralEnd.fileComment = "";

		centralEnd.write(this);

		if (out != null)
			try {
				out.close();
			} catch (Throwable t) {
			}
	}

}
