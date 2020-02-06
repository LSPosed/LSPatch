package net.fornwall.apksigner;

import org.spongycastle.cert.jcajce.JcaCertStore;
import org.spongycastle.cms.*;
import org.spongycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.DigestCalculatorProvider;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;
import org.spongycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.spongycastle.util.Store;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class SignatureBlockGenerator {

	/**
	 * Sign the given content using the private and public keys from the keySet and return the encoded CMS (PKCS#7)
	 * data. Use of direct signature and DER encoding produces a block that is verifiable by Android recovery programs.
	 */
	public static byte[] generate(KeySet keySet, byte[] content) {
		try {
			List<X509Certificate> certList = new ArrayList<>();
			CMSTypedData msg = new CMSProcessableByteArray(content);

			certList.add(keySet.publicKey);

			Store certs = new JcaCertStore(certList);

			CMSSignedDataGenerator gen = new CMSSignedDataGenerator();

			JcaContentSignerBuilder jcaContentSignerBuilder = new JcaContentSignerBuilder(keySet.signatureAlgorithm)
					.setProvider("SC");
			ContentSigner sha1Signer = jcaContentSignerBuilder.build(keySet.privateKey);

			JcaDigestCalculatorProviderBuilder jcaDigestCalculatorProviderBuilder = new JcaDigestCalculatorProviderBuilder()
					.setProvider("SC");
			DigestCalculatorProvider digestCalculatorProvider = jcaDigestCalculatorProviderBuilder.build();

			JcaSignerInfoGeneratorBuilder jcaSignerInfoGeneratorBuilder = new JcaSignerInfoGeneratorBuilder(
					digestCalculatorProvider);
			jcaSignerInfoGeneratorBuilder.setDirectSignature(true);
			SignerInfoGenerator signerInfoGenerator = jcaSignerInfoGeneratorBuilder.build(sha1Signer, keySet.publicKey);

			gen.addSignerInfoGenerator(signerInfoGenerator);
			gen.addCertificates(certs);

			CMSSignedData sigData = gen.generate(msg, false);
			return sigData.toASN1Structure().getEncoded("DER");
		} catch (Exception x) {
			throw new RuntimeException(x.getMessage(), x);
		}
	}

}
