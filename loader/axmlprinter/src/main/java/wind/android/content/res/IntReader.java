/*
 * Copyright 2008 Android4ME
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wind.android.content.res;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Dmitry Skiba
 *
 * Simple helper class that allows reading of integers.
 * 
 * TODO:
 * 	* implement buffering
 *
 */
public final class IntReader {
	
	public IntReader() {
	}
	public IntReader(InputStream stream,boolean bigEndian) {
		reset(stream,bigEndian);
	}
	
	public final void reset(InputStream stream,boolean bigEndian) {
		m_stream=stream;
		m_bigEndian=bigEndian;
		m_position=0;		
	}

	public final void close() {
		if (m_stream==null) {
			return;
		}
		try {
			m_stream.close();
		}
		catch (IOException e) {
		}
		reset(null,false);
	}
	
	public final InputStream getStream() {
		return m_stream;
	}
	
	public final boolean isBigEndian() {
		return m_bigEndian;
	}
	public final void setBigEndian(boolean bigEndian) {
		m_bigEndian=bigEndian;
	}

	public final int readByte() throws IOException {
		return readInt(1);
	}
	public final int readShort() throws IOException {
		return readInt(2);
	}
	public final int readInt() throws IOException {
		return readInt(4);
	}
    public final void readFully(byte[] b) throws IOException {
        new DataInputStream(m_stream).readFully(b);
    }
	public final int readInt(int length) throws IOException {
		if (length<0 || length>4) {
			throw new IllegalArgumentException();
		}
		int result=0;
		if (m_bigEndian) {
			for (int i=(length-1)*8;i>=0;i-=8) {
				int b=m_stream.read();
				if (b==-1) {
					throw new EOFException();
				}
				m_position+=1;
				result|=(b<<i);
			}
		} else {
			length*=8;
			for (int i=0;i!=length;i+=8) {
				int b=m_stream.read();
				if (b==-1) {
					throw new EOFException();
				}
				m_position+=1;
				result|=(b<<i);
			}
		}
		return result;		  
	}
	
	public final int[] readIntArray(int length) throws IOException {
		int[] array=new int[length];
		readIntArray(array,0,length);
		return array;
	}
	
	public final void readIntArray(int[] array,int offset,int length) throws IOException {
		for (;length>0;length-=1) {
			array[offset++]=readInt();
		}
	}
	
	public final byte[] readByteArray(int length) throws IOException {
		byte[] array=new byte[length];
		int read=m_stream.read(array);
		m_position+=read;
		if (read!=length) {
			throw new EOFException();
		}
		return array;
	}
	
	public final void skip(int bytes) throws IOException {
		if (bytes<=0) {
			return;
		}
		long skipped=m_stream.skip(bytes);
		m_position+=skipped;
		if (skipped!=bytes) {
			throw new EOFException();
		}
	}
	
	public final void skipInt() throws IOException {
		skip(4);
	}
	
	public final int available() throws IOException {
		return m_stream.available();
	}
	
	public final int getPosition() {
		return m_position;
	}
	
	/////////////////////////////////// data

	private InputStream m_stream;
	private boolean m_bigEndian;
	private int m_position;
}
