package net.fornwall.apksigner;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class KeySet {

	/** Certificate. */
	public final X509Certificate publicKey;
	/** Private key. */
	public final PrivateKey privateKey;
	public final String signatureAlgorithm;

	public KeySet(X509Certificate publicKey, PrivateKey privateKey, String signatureAlgorithm) {
		this.publicKey = publicKey;
		this.privateKey = privateKey;
		this.signatureAlgorithm = (signatureAlgorithm != null) ? signatureAlgorithm : "SHA1withRSA";
	}

}
