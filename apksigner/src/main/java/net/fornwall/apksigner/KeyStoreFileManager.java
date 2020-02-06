package net.fornwall.apksigner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;

import org.spongycastle.jce.provider.BouncyCastleProvider;

public class KeyStoreFileManager {

	public static final Provider SECURITY_PROVIDER = new BouncyCastleProvider();

	static {
		// Add the spongycastle version of the BC provider so that the implementation classes returned from the keystore
		// are all from the spongycastle libs.
		Security.addProvider(SECURITY_PROVIDER);
	}

	private static class JksKeyStore extends KeyStore {
		public JksKeyStore() {
			super(new JKS(), SECURITY_PROVIDER, "jks");
		}
	}

	public static KeyStore createKeyStore(char[] password) throws Exception {
		KeyStore ks = new JksKeyStore();
		ks.load(null, password);
		return ks;
	}

	public static KeyStore loadKeyStore(String keystorePath, char[] password) throws Exception {
		KeyStore ks = new JksKeyStore();
		try (FileInputStream fis = new FileInputStream(keystorePath)) {
			ks.load(fis, password);
		}
		return ks;
	}

	public static void writeKeyStore(KeyStore ks, String keystorePath, char[] password) throws Exception {
		File keystoreFile = new File(keystorePath);
		try {
			if (keystoreFile.exists()) {
				// I've had some trouble saving new versions of the key store file in which the file becomes
				// empty/corrupt. Saving the new version to a new file and creating a backup of the old version.
				File tmpFile = File.createTempFile(keystoreFile.getName(), null, keystoreFile.getParentFile());
				try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
					ks.store(fos, password);
				}
				/*
				 * create a backup of the previous version int i = 1; File backup = new File( keystorePath + "." + i +
				 * ".bak"); while (backup.exists()) { i += 1; backup = new File( keystorePath + "." + i + ".bak"); }
				 * renameTo(keystoreFile, backup);
				 */
				renameTo(tmpFile, keystoreFile);
			} else {
				try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
					ks.store(fos, password);
				}
			}
		} catch (Exception x) {
			try {
				File logfile = File.createTempFile("zipsigner-error", ".log", keystoreFile.getParentFile());
				try (PrintWriter pw = new PrintWriter(new FileWriter(logfile))) {
					x.printStackTrace(pw);
				}
			} catch (Exception y) {
			}
			throw x;
		}
	}

	static void copyFile(File srcFile, File destFile, boolean preserveFileDate) throws IOException {
		if (destFile.exists() && destFile.isDirectory())
			throw new IOException("Destination '" + destFile + "' exists but is a directory");
		try (FileInputStream input = new FileInputStream(srcFile)) {
			try (FileOutputStream output = new FileOutputStream(destFile)) {
				byte[] buffer = new byte[4096];
				int n = 0;
				while (-1 != (n = input.read(buffer))) {
					output.write(buffer, 0, n);
				}
			}
		}

		if (srcFile.length() != destFile.length()) {
			throw new IOException("Failed to copy full contents from '" + srcFile + "' to '" + destFile + "'");
		}
		if (preserveFileDate)
			destFile.setLastModified(srcFile.lastModified());
	}

	public static void renameTo(File fromFile, File toFile) throws IOException {
		copyFile(fromFile, toFile, true);
		if (!fromFile.delete())
			throw new IOException("Failed to delete " + fromFile);
	}

}
