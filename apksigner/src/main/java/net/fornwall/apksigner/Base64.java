package net.fornwall.apksigner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.spongycastle.util.encoders.Base64Encoder;

/** Base64 encoding handling in a portable way across Android and JSE. */
public class Base64 {

	public static String encode(byte[] data) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			new Base64Encoder().encode(data, 0, data.length, baos);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new String(baos.toByteArray());
	}

}