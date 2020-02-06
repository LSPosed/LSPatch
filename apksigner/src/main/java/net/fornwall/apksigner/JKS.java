/* JKS.java -- implementation of the "JKS" key store.
   Copyright (C) 2003  Casey Marshall <rsdio@metastatic.org>

Permission to use, copy, modify, distribute, and sell this software and
its documentation for any purpose is hereby granted without fee,
provided that the above copyright notice appear in all copies and that
both that copyright notice and this permission notice appear in
supporting documentation.  No representations are made about the
suitability of this software for any purpose.  It is provided "as is"
without express or implied warranty.

This program was derived by reverse-engineering Sun's own
implementation, using only the public API that is available in the 1.4.1
JDK.  Hence nothing in this program is, or is derived from, anything
copyrighted by Sun Microsystems.  While the "Binary Evaluation License
Agreement" that the JDK is licensed under contains blanket statements
that forbid reverse-engineering (among other things), it is my position
that US copyright law does not and cannot forbid reverse-engineering of
software to produce a compatible implementation.  There are, in fact,
numerous clauses in copyright law that specifically allow
reverse-engineering, and therefore I believe it is outside of Sun's
power to enforce restrictions on reverse-engineering of their software,
and it is irresponsible for them to claim they can.  */

package net.fornwall.apksigner;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.spec.SecretKeySpec;

/**
 * This is an implementation of Sun's proprietary key store algorithm, called "JKS" for "Java Key Store". This
 * implementation was created entirely through reverse-engineering.
 *
 * <p>
 * The format of JKS files is, from the start of the file:
 *
 * <ol>
 * <li>Magic bytes. This is a four-byte integer, in big-endian byte order, equal to <code>0xFEEDFEED</code>.</li>
 * <li>The version number (probably), as a four-byte integer (all multibyte integral types are in big-endian byte
 * order). The current version number (in modern distributions of the JDK) is 2.</li>
 * <li>The number of entries in this keystore, as a four-byte integer. Call this value <i>n</i></li>
 * <li>Then, <i>n</i> times:
 * <ol>
 * <li>The entry type, a four-byte int. The value 1 denotes a private key entry, and 2 denotes a trusted certificate.</li>
 * <li>The entry's alias, formatted as strings such as those written by <a
 * href="http://java.sun.com/j2se/1.4.1/docs/api/java/io/DataOutput.html#writeUTF(java.lang.String)"
 * >DataOutput.writeUTF(String)</a>.</li>
 * <li>An eight-byte integer, representing the entry's creation date, in milliseconds since the epoch.
 *
 * <p>
 * Then, if the entry is a private key entry:
 * <ol>
 * <li>The size of the encoded key as a four-byte int, then that number of bytes. The encoded key is the DER encoded
 * bytes of the <a
 * href="http://java.sun.com/j2se/1.4.1/docs/api/javax/crypto/EncryptedPrivateKeyInfo.html">EncryptedPrivateKeyInfo</a>
 * structure (the encryption algorithm is discussed later).</li>
 * <li>A four-byte integer, followed by that many encoded certificates, encoded as described in the trusted certificates
 * section.</li>
 * </ol>
 *
 * <p>
 * Otherwise, the entry is a trusted certificate, which is encoded as the name of the encoding algorithm (e.g. X.509),
 * encoded the same way as alias names. Then, a four-byte integer representing the size of the encoded certificate, then
 * that many bytes representing the encoded certificate (e.g. the DER bytes in the case of X.509).</li>
 * </ol>
 * </li>
 * <li>Then, the signature.</li>
 * </ol>
 * </ol> </li> </ol>
 *
 * <p>
 * (See <a href="http://metastatic.org/source/genkey.java">this file</a> for some idea of how I was able to figure out these algorithms)
 * </p>
 *
 * <p>
 * Decrypting the key works as follows:
 *
 * <ol>
 * <li>The key length is the length of the ciphertext minus 40. The encrypted key, <code>ekey</code>, is the middle
 * bytes of the ciphertext.</li>
 * <li>Take the first 20 bytes of the encrypted key as a seed value, <code>K[0]</code>.</li>
 * <li>Compute <code>K[1] ... K[n]</code>, where <code>|K[i]| = 20</code>, <code>n = ceil(|ekey| / 20)</code>, and
 * <code>K[i] = SHA-1(UTF-16BE(password) + K[i-1])</code>.</li>
 * <li><code>key = ekey ^ (K[1] + ... + K[n])</code>.</li>
 * <li>The last 20 bytes are the checksum, computed as <code>H =
 * SHA-1(UTF-16BE(password) + key)</code>. If this value does not match the last 20 bytes of the ciphertext, output
 * <code>FAIL</code>. Otherwise, output <code>key</code>.</li>
 * </ol>
 *
 * <p>
 * The signature is defined as <code>SHA-1(UTF-16BE(password) +
 * US_ASCII("Mighty Aphrodite") + encoded_keystore)</code> (yup, Sun engineers are just that clever).
 *
 * <p>
 * (Above, SHA-1 denotes the secure hash algorithm, UTF-16BE the big-endian byte representation of a UTF-16 string, and
 * US_ASCII the ASCII byte representation of the string.)
 *
 * <p>
 * The original source code by Casey Marshall of this class should be available in the file <a
 * href="http://metastatic.org/source/JKS.java">http://metastatic.org/source/JKS.java</a>.
 *
 * <p>
 * Changes by Ken Ellinwood:
 * <ul>
 * <li>Fixed a NullPointerException in engineLoad(). This method must return gracefully if the keystore input stream is
 * null.</li>
 * <li>engineGetCertificateEntry() was updated to return the first cert in the chain for private key entries.</li>
 * <li>Lowercase the alias names, otherwise keytool chokes on the file created by this code.</li>
 * <li>Fixed the integrity check in engineLoad(), previously the exception was never thrown regardless of password
 * value.</li>
 * </ul>
 * 
 * @author Casey Marshall (rsdio@metastatic.org)
 * @author Ken Ellinwood
 */
public class JKS extends KeyStoreSpi {

	/** Ah, Sun. So goddamned clever with those magic bytes. */
	private static final int MAGIC = 0xFEEDFEED;

	private static final int PRIVATE_KEY = 1;
	private static final int TRUSTED_CERT = 2;

	private final Vector<String> aliases = new Vector<>();
	private final HashMap<String, Certificate> trustedCerts = new HashMap<>();
	private final HashMap<String, byte[]> privateKeys = new HashMap<>();
	private final HashMap<String, Certificate[]> certChains = new HashMap<>();
	private final HashMap<String, Date> dates = new HashMap<>();

	@Override
	public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
		alias = alias.toLowerCase();

		if (!privateKeys.containsKey(alias))
			return null;
		byte[] key = decryptKey(privateKeys.get(alias), charsToBytes(password));
		Certificate[] chain = engineGetCertificateChain(alias);
		if (chain.length > 0) {
			try {
				// Private and public keys MUST have the same algorithm.
				KeyFactory fact = KeyFactory.getInstance(chain[0].getPublicKey().getAlgorithm());
				return fact.generatePrivate(new PKCS8EncodedKeySpec(key));
			} catch (InvalidKeySpecException x) {
				throw new UnrecoverableKeyException(x.getMessage());
			}
		} else
			return new SecretKeySpec(key, alias);
	}

	@Override
	public Certificate[] engineGetCertificateChain(String alias) {
		return certChains.get(alias.toLowerCase());
	}

	@Override
	public Certificate engineGetCertificate(String alias) {
		alias = alias.toLowerCase();
		if (engineIsKeyEntry(alias)) {
			Certificate[] certChain = certChains.get(alias);
			if (certChain != null && certChain.length > 0)
				return certChain[0];
		}
		return trustedCerts.get(alias);
	}

	@Override
	public Date engineGetCreationDate(String alias) {
		alias = alias.toLowerCase();
		return dates.get(alias);
	}

	// XXX implement writing methods.
	@Override
	public void engineSetKeyEntry(String alias, Key key, char[] passwd, Certificate[] certChain)
			throws KeyStoreException {
		alias = alias.toLowerCase();
		if (trustedCerts.containsKey(alias))
			throw new KeyStoreException("\"" + alias + " is a trusted certificate entry");
		privateKeys.put(alias, encryptKey(key, charsToBytes(passwd)));
		if (certChain != null)
			certChains.put(alias, certChain);
		else
			certChains.put(alias, new Certificate[0]);
		if (!aliases.contains(alias)) {
			dates.put(alias, new Date());
			aliases.add(alias);
		}
	}

	@SuppressWarnings("unused")
	@Override
	public void engineSetKeyEntry(String alias, byte[] encodedKey, Certificate[] certChain) throws KeyStoreException {
		alias = alias.toLowerCase();
		if (trustedCerts.containsKey(alias))
			throw new KeyStoreException("\"" + alias + "\" is a trusted certificate entry");
		try {
			new EncryptedPrivateKeyInfo(encodedKey);
		} catch (IOException ioe) {
			throw new KeyStoreException("encoded key is not an EncryptedPrivateKeyInfo");
		}
		privateKeys.put(alias, encodedKey);
		if (certChain != null)
			certChains.put(alias, certChain);
		else
			certChains.put(alias, new Certificate[0]);
		if (!aliases.contains(alias)) {
			dates.put(alias, new Date());
			aliases.add(alias);
		}
	}

	@Override
	public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
		alias = alias.toLowerCase();
		if (privateKeys.containsKey(alias))
			throw new KeyStoreException("\"" + alias + "\" is a private key entry");
		if (cert == null)
			throw new NullPointerException();
		trustedCerts.put(alias, cert);
		if (!aliases.contains(alias)) {
			dates.put(alias, new Date());
			aliases.add(alias);
		}
	}

	@Override
	public void engineDeleteEntry(String alias) throws KeyStoreException {
		alias = alias.toLowerCase();
		aliases.remove(alias);
	}

	@Override
	public Enumeration<String> engineAliases() {
		return aliases.elements();
	}

	@Override
	public boolean engineContainsAlias(String alias) {
		alias = alias.toLowerCase();
		return aliases.contains(alias);
	}

	@Override
	public int engineSize() {
		return aliases.size();
	}

	@Override
	public boolean engineIsKeyEntry(String alias) {
		alias = alias.toLowerCase();
		return privateKeys.containsKey(alias);
	}

	@Override
	public boolean engineIsCertificateEntry(String alias) {
		alias = alias.toLowerCase();
		return trustedCerts.containsKey(alias);
	}

	@Override
	public String engineGetCertificateAlias(Certificate cert) {
		for (String alias : trustedCerts.keySet())
			if (cert.equals(trustedCerts.get(alias)))
				return alias;
		return null;
	}

	@Override
	public void engineStore(OutputStream out, char[] passwd) throws IOException, NoSuchAlgorithmException,
			CertificateException {
		MessageDigest md = MessageDigest.getInstance("SHA1");
		md.update(charsToBytes(passwd));
		md.update("Mighty Aphrodite".getBytes(StandardCharsets.UTF_8));
		DataOutputStream dout = new DataOutputStream(new DigestOutputStream(out, md));
		dout.writeInt(MAGIC);
		dout.writeInt(2);
		dout.writeInt(aliases.size());
		for (Enumeration<String> e = aliases.elements(); e.hasMoreElements();) {
			String alias = e.nextElement();
			if (trustedCerts.containsKey(alias)) {
				dout.writeInt(TRUSTED_CERT);
				dout.writeUTF(alias);
				dout.writeLong(dates.get(alias).getTime());
				writeCert(dout, trustedCerts.get(alias));
			} else {
				dout.writeInt(PRIVATE_KEY);
				dout.writeUTF(alias);
				dout.writeLong(dates.get(alias).getTime());
				byte[] key = privateKeys.get(alias);
				dout.writeInt(key.length);
				dout.write(key);
				Certificate[] chain = certChains.get(alias);
				dout.writeInt(chain.length);
				for (int i = 0; i < chain.length; i++)
					writeCert(dout, chain[i]);
			}
		}
		byte[] digest = md.digest();
		dout.write(digest);
	}

	@Override
	public void engineLoad(InputStream in, char[] passwd) throws IOException, NoSuchAlgorithmException,
			CertificateException {
		MessageDigest md = MessageDigest.getInstance("SHA");
		if (passwd != null)
			md.update(charsToBytes(passwd));
		md.update("Mighty Aphrodite".getBytes(StandardCharsets.UTF_8));

		aliases.clear();
		trustedCerts.clear();
		privateKeys.clear();
		certChains.clear();
		dates.clear();
		if (in == null)
			return;
		DataInputStream din = new DataInputStream(new DigestInputStream(in, md));
		if (din.readInt() != MAGIC)
			throw new IOException("not a JavaKeyStore");
		din.readInt(); // version no.
		final int n = din.readInt();
		aliases.ensureCapacity(n);
		if (n < 0)
			throw new LoadKeystoreException("Malformed key store");
		for (int i = 0; i < n; i++) {
			int type = din.readInt();
			String alias = din.readUTF();
			aliases.add(alias);
			dates.put(alias, new Date(din.readLong()));
			switch (type) {
			case PRIVATE_KEY:
				int len = din.readInt();
				byte[] encoded = new byte[len];
				din.read(encoded);
				privateKeys.put(alias, encoded);
				int count = din.readInt();
				Certificate[] chain = new Certificate[count];
				for (int j = 0; j < count; j++)
					chain[j] = readCert(din);
				certChains.put(alias, chain);
				break;

			case TRUSTED_CERT:
				trustedCerts.put(alias, readCert(din));
				break;

			default:
				throw new LoadKeystoreException("Malformed key store");
			}
		}

		if (passwd != null) {
			byte[] computedHash = md.digest();
			byte[] storedHash = new byte[20];
			din.read(storedHash);
			if (!MessageDigest.isEqual(storedHash, computedHash)) {
				throw new LoadKeystoreException("Incorrect password, or integrity check failed.");
			}
		}
	}

	// Own methods.
	// ------------------------------------------------------------------------

	private static Certificate readCert(DataInputStream in) throws IOException, CertificateException {
		String type = in.readUTF();
		int len = in.readInt();
		byte[] encoded = new byte[len];
		in.read(encoded);
		CertificateFactory factory = CertificateFactory.getInstance(type);
		return factory.generateCertificate(new ByteArrayInputStream(encoded));
	}

	private static void writeCert(DataOutputStream dout, Certificate cert) throws IOException, CertificateException {
		dout.writeUTF(cert.getType());
		byte[] b = cert.getEncoded();
		dout.writeInt(b.length);
		dout.write(b);
	}

	private static byte[] decryptKey(byte[] encryptedPKI, byte[] passwd) throws UnrecoverableKeyException {
		try {
			EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(encryptedPKI);
			byte[] encr = epki.getEncryptedData();
			byte[] keystream = new byte[20];
			System.arraycopy(encr, 0, keystream, 0, 20);
			byte[] check = new byte[20];
			System.arraycopy(encr, encr.length - 20, check, 0, 20);
			byte[] key = new byte[encr.length - 40];
			MessageDigest sha = MessageDigest.getInstance("SHA1");
			int count = 0;
			while (count < key.length) {
				sha.reset();
				sha.update(passwd);
				sha.update(keystream);
				sha.digest(keystream, 0, keystream.length);
				for (int i = 0; i < keystream.length && count < key.length; i++) {
					key[count] = (byte) (keystream[i] ^ encr[count + 20]);
					count++;
				}
			}
			sha.reset();
			sha.update(passwd);
			sha.update(key);
			if (!MessageDigest.isEqual(check, sha.digest()))
				throw new UnrecoverableKeyException("checksum mismatch");
			return key;
		} catch (Exception x) {
			throw new UnrecoverableKeyException(x.getMessage());
		}
	}

	private static byte[] encryptKey(Key key, byte[] passwd) throws KeyStoreException {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA1");
			// SecureRandom rand = SecureRandom.getInstance("SHA1PRNG");
			byte[] k = key.getEncoded();
			byte[] encrypted = new byte[k.length + 40];
			byte[] keystream = SecureRandom.getSeed(20);
			System.arraycopy(keystream, 0, encrypted, 0, 20);
			int count = 0;
			while (count < k.length) {
				sha.reset();
				sha.update(passwd);
				sha.update(keystream);
				sha.digest(keystream, 0, keystream.length);
				for (int i = 0; i < keystream.length && count < k.length; i++) {
					encrypted[count + 20] = (byte) (keystream[i] ^ k[count]);
					count++;
				}
			}
			sha.reset();
			sha.update(passwd);
			sha.update(k);
			sha.digest(encrypted, encrypted.length - 20, 20);
			// 1.3.6.1.4.1.42.2.17.1.1 is Sun's private OID for this encryption algorithm.
			return new EncryptedPrivateKeyInfo("1.3.6.1.4.1.42.2.17.1.1", encrypted).getEncoded();
		} catch (Exception x) {
			throw new KeyStoreException(x.getMessage());
		}
	}

	private static byte[] charsToBytes(char[] passwd) {
		byte[] buf = new byte[passwd.length * 2];
		for (int i = 0, j = 0; i < passwd.length; i++) {
			buf[j++] = (byte) (passwd[i] >>> 8);
			buf[j++] = (byte) passwd[i];
		}
		return buf;
	}
}
