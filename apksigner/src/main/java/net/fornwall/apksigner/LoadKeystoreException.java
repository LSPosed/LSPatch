package net.fornwall.apksigner;

import java.io.IOException;

/** Thrown by JKS.engineLoad() for errors that occur after determining the keystore is actually a JKS keystore. */
@SuppressWarnings("serial")
public class LoadKeystoreException extends IOException {

	public LoadKeystoreException(String message) {
		super(message);
	}

}
