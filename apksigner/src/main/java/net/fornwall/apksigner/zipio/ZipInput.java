package net.fornwall.apksigner.zipio;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Manifest;

public final class ZipInput implements AutoCloseable {

	final RandomAccessFile in;
	final long fileLength;
	int scanIterations = 0;

	public final Map<String, ZioEntry> entries = new LinkedHashMap<>();
	final CentralEnd centralEnd;
	Manifest manifest;

	public ZipInput(String filename) throws IOException {
		in = new RandomAccessFile(filename, "r");
		fileLength = in.length();

		long posEOCDR = scanForEOCDR((int) Math.min(fileLength, 256));
		in.seek(posEOCDR);
		centralEnd = CentralEnd.read(this);
		in.seek(centralEnd.centralStartOffset);

		for (int i = 0; i < centralEnd.totalCentralEntries; i++) {
			ZioEntry entry = new ZioEntry(this);
			entries.put(entry.getName(), entry);
		}
	}

	public Manifest getManifest() throws IOException {
		if (manifest == null) {
			ZioEntry e = entries.get("META-INF/MANIFEST.MF");
			if (e != null)
				manifest = new Manifest(e.getInputStream());
		}
		return manifest;
	}

	/**
	 * Scan the end of the file for the end of central directory record (EOCDR). Returns the file offset of the EOCD
	 * signature. The size parameter is an initial buffer size (e.g., 256).
	 */
	public long scanForEOCDR(int size) throws IOException {
		if (size > fileLength || size > 65536)
			throw new IllegalStateException("End of central directory not found");

		int scanSize = (int) Math.min(fileLength, size);

		byte[] scanBuf = new byte[scanSize];
		in.seek(fileLength - scanSize);
		in.readFully(scanBuf);

		for (int i = scanSize - 22; i >= 0; i--) {
			scanIterations += 1;
			if (scanBuf[i] == 0x50 && scanBuf[i + 1] == 0x4b && scanBuf[i + 2] == 0x05 && scanBuf[i + 3] == 0x06) {
				return fileLength - scanSize + i;
			}
		}

		return scanForEOCDR(size * 2);
	}

	@Override
	public void close() {
		if (in != null)
			try {
				in.close();
			} catch (Throwable t) {
			}
	}

	public long getFilePointer() throws IOException {
		return in.getFilePointer();
	}

	public void seek(long position) throws IOException {
		in.seek(position);
	}

	public int readInt() throws IOException {
		int result = 0;
		for (int i = 0; i < 4; i++)
			result |= (in.readUnsignedByte() << (8 * i));
		return result;
	}

	public short readShort() throws IOException {
		short result = 0;
		for (int i = 0; i < 2; i++)
			result |= (in.readUnsignedByte() << (8 * i));
		return result;
	}

	public String readString(int length) throws IOException {
		byte[] buffer = new byte[length];
		for (int i = 0; i < length; i++)
			buffer[i] = in.readByte();
		return new String(buffer);
	}

	public byte[] readBytes(int length) throws IOException {
		byte[] buffer = new byte[length];
		for (int i = 0; i < length; i++) {
			buffer[i] = in.readByte();
		}
		return buffer;
	}

}
