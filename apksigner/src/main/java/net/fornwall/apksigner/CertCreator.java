package net.fornwall.apksigner;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.asn1.x500.style.BCStyle;
import org.spongycastle.jce.X509Principal;
import org.spongycastle.x509.X509V3CertificateGenerator;

/** All methods create self-signed certificates. */
public class CertCreator {

	/** Helper class for dealing with the distinguished name RDNs. */
	@SuppressWarnings("serial")
	public static class DistinguishedNameValues extends LinkedHashMap<ASN1ObjectIdentifier, String> {

		public DistinguishedNameValues() {
			put(BCStyle.C, null);
			put(BCStyle.ST, null);
			put(BCStyle.L, null);
			put(BCStyle.STREET, null);
			put(BCStyle.O, null);
			put(BCStyle.OU, null);
			put(BCStyle.CN, null);
		}

		@Override
		public String put(ASN1ObjectIdentifier oid, String value) {
			if (value != null && value.equals(""))
				value = null;
			if (containsKey(oid))
				super.put(oid, value); // preserve original ordering
			else {
				super.put(oid, value);
				// String cn = remove(BCStyle.CN); // CN will always be last.
				// put(BCStyle.CN,cn);
			}
			return value;
		}

		public void setCountry(String country) {
			put(BCStyle.C, country);
		}

		public void setState(String state) {
			put(BCStyle.ST, state);
		}

		public void setLocality(String locality) {
			put(BCStyle.L, locality);
		}

		public void setStreet(String street) {
			put(BCStyle.STREET, street);
		}

		public void setOrganization(String organization) {
			put(BCStyle.O, organization);
		}

		public void setOrganizationalUnit(String organizationalUnit) {
			put(BCStyle.OU, organizationalUnit);
		}

		public void setCommonName(String commonName) {
			put(BCStyle.CN, commonName);
		}

		@Override
		public int size() {
			int result = 0;
			for (String value : values()) {
				if (value != null)
					result += 1;
			}
			return result;
		}

		public X509Principal getPrincipal() {
			Vector<ASN1ObjectIdentifier> oids = new Vector<>();
			Vector<String> values = new Vector<>();
			for (Map.Entry<ASN1ObjectIdentifier, String> entry : entrySet()) {
				if (entry.getValue() != null && !entry.getValue().equals("")) {
					oids.add(entry.getKey());
					values.add(entry.getValue());
				}
			}
			return new X509Principal(oids, values);
		}
	}

	public static KeySet createKeystoreAndKey(String storePath, char[] storePass, String keyAlgorithm, int keySize,
			String keyName, char[] keyPass, String certSignatureAlgorithm, int certValidityYears,
			DistinguishedNameValues distinguishedNameValues) {
		try {
			KeySet keySet = createKey(keyAlgorithm, keySize, certSignatureAlgorithm, certValidityYears,
					distinguishedNameValues);

			KeyStore privateKS = KeyStoreFileManager.createKeyStore(storePass);
			privateKS.setKeyEntry(keyName, keySet.privateKey, keyPass,
					new java.security.cert.Certificate[] { keySet.publicKey });

			File sfile = new File(storePath);
			if (sfile.exists()) {
				throw new IOException("File already exists: " + storePath);
			}
			KeyStoreFileManager.writeKeyStore(privateKS, storePath, storePass);

			return keySet;
		} catch (RuntimeException x) {
			throw x;
		} catch (Exception x) {
			throw new RuntimeException(x.getMessage(), x);
		}
	}

	private static KeySet createKey(String keyAlgorithm, int keySize, String certSignatureAlgorithm,
			int certValidityYears, DistinguishedNameValues distinguishedNameValues) {
		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(keyAlgorithm);
			keyPairGenerator.initialize(keySize);
			KeyPair KPair = keyPairGenerator.generateKeyPair();

			X509V3CertificateGenerator v3CertGen = new X509V3CertificateGenerator();
			X509Principal principal = distinguishedNameValues.getPrincipal();

			// generate a positive serial number
			BigInteger serialNumber = BigInteger.valueOf(new SecureRandom().nextInt());
			while (serialNumber.compareTo(BigInteger.ZERO) < 0)
				serialNumber = BigInteger.valueOf(new SecureRandom().nextInt());
			v3CertGen.setSerialNumber(serialNumber);
			v3CertGen.setIssuerDN(principal);
			v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60L * 60L * 24L * 30L));
			v3CertGen.setNotAfter(
					new Date(System.currentTimeMillis() + (1000L * 60L * 60L * 24L * 366L * certValidityYears)));
			v3CertGen.setSubjectDN(principal);
			v3CertGen.setPublicKey(KPair.getPublic());
			v3CertGen.setSignatureAlgorithm(certSignatureAlgorithm);

			X509Certificate PKCertificate = v3CertGen.generate(KPair.getPrivate(),
					KeyStoreFileManager.SECURITY_PROVIDER.getName());
			return new KeySet(PKCertificate, KPair.getPrivate(), null);
		} catch (Exception x) {
			throw new RuntimeException(x.getMessage(), x);
		}
	}
}
