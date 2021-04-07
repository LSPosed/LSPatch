/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.apksigner;

import com.android.apksig.SigningCertificateLineage.SignerCapabilities;
import com.android.apksig.internal.util.X509CertificateUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** A utility class to load private key and certificates from a keystore or key and cert files. */
public class SignerParams {
    private String name;

    private String keystoreFile;
    private String keystoreKeyAlias;
    private String keystorePasswordSpec;
    private String keyPasswordSpec;
    private Charset passwordCharset;
    private String keystoreType;
    private String keystoreProviderName;
    private String keystoreProviderClass;
    private String keystoreProviderArg;

    private String keyFile;
    private String certFile;

    private String v1SigFileBasename;

    private PrivateKey privateKey;
    private List<X509Certificate> certs;
    private final SignerCapabilities.Builder signerCapabilitiesBuilder =
            new SignerCapabilities.Builder();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setKeystoreFile(String keystoreFile) {
        this.keystoreFile = keystoreFile;
    }

    public String getKeystoreKeyAlias() {
        return keystoreKeyAlias;
    }

    public void setKeystoreKeyAlias(String keystoreKeyAlias) {
        this.keystoreKeyAlias = keystoreKeyAlias;
    }

    public void setKeystorePasswordSpec(String keystorePasswordSpec) {
        this.keystorePasswordSpec = keystorePasswordSpec;
    }

    public void setKeyPasswordSpec(String keyPasswordSpec) {
        this.keyPasswordSpec = keyPasswordSpec;
    }

    public void setPasswordCharset(Charset passwordCharset) {
        this.passwordCharset = passwordCharset;
    }

    public void setKeystoreType(String keystoreType) {
        this.keystoreType = keystoreType;
    }

    public void setKeystoreProviderName(String keystoreProviderName) {
        this.keystoreProviderName = keystoreProviderName;
    }

    public void setKeystoreProviderClass(String keystoreProviderClass) {
        this.keystoreProviderClass = keystoreProviderClass;
    }

    public void setKeystoreProviderArg(String keystoreProviderArg) {
        this.keystoreProviderArg = keystoreProviderArg;
    }

    public String getKeyFile() {
        return keyFile;
    }

    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    public String getV1SigFileBasename() {
        return v1SigFileBasename;
    }

    public void setV1SigFileBasename(String v1SigFileBasename) {
        this.v1SigFileBasename = v1SigFileBasename;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public List<X509Certificate> getCerts() {
        return certs;
    }

    public SignerCapabilities.Builder getSignerCapabilitiesBuilder() {
        return signerCapabilitiesBuilder;
    }

    boolean isEmpty() {
        return (name == null)
                && (keystoreFile == null)
                && (keystoreKeyAlias == null)
                && (keystorePasswordSpec == null)
                && (keyPasswordSpec == null)
                && (passwordCharset == null)
                && (keystoreType == null)
                && (keystoreProviderName == null)
                && (keystoreProviderClass == null)
                && (keystoreProviderArg == null)
                && (keyFile == null)
                && (certFile == null)
                && (v1SigFileBasename == null)
                && (privateKey == null)
                && (certs == null);
    }

    public void loadPrivateKeyAndCerts(PasswordRetriever passwordRetriever) throws Exception {
        if (keystoreFile != null) {
            if (keyFile != null) {
                throw new ParameterException(
                        "--ks and --key may not be specified at the same time");
            } else if (certFile != null) {
                throw new ParameterException(
                        "--ks and --cert may not be specified at the same time");
            }
            loadPrivateKeyAndCertsFromKeyStore(passwordRetriever);
        } else if (keyFile != null) {
            loadPrivateKeyAndCertsFromFiles(passwordRetriever);
        } else {
            throw new ParameterException(
                    "KeyStore (--ks) or private key file (--key) must be specified");
        }
    }

    private void loadPrivateKeyAndCertsFromKeyStore(PasswordRetriever passwordRetriever)
            throws Exception {
        if (keystoreFile == null) {
            throw new ParameterException("KeyStore (--ks) must be specified");
        }

        // 1. Obtain a KeyStore implementation
        String ksType = (keystoreType != null) ? keystoreType : KeyStore.getDefaultType();
        KeyStore ks;
        if (keystoreProviderName != null) {
            // Use a named Provider (assumes the provider is already installed)
            ks = KeyStore.getInstance(ksType, keystoreProviderName);
        } else if (keystoreProviderClass != null) {
            // Use a new Provider instance (does not require the provider to be installed)
            Class<?> ksProviderClass = Class.forName(keystoreProviderClass);
            if (!Provider.class.isAssignableFrom(ksProviderClass)) {
                throw new ParameterException(
                        "Keystore Provider class " + keystoreProviderClass + " not subclass of "
                                + Provider.class.getName());
            }
            Provider ksProvider;
            if (keystoreProviderArg != null) {
                // Single-arg Provider constructor
                ksProvider =
                        (Provider) ksProviderClass.getConstructor(String.class)
                                .newInstance(keystoreProviderArg);
            } else {
                // No-arg Provider constructor
                ksProvider = (Provider) ksProviderClass.getConstructor().newInstance();
            }
            ks = KeyStore.getInstance(ksType, ksProvider);
        } else {
            // Use the highest-priority Provider which offers the requested KeyStore type
            ks = KeyStore.getInstance(ksType);
        }

        // 2. Load the KeyStore
        List<char[]> keystorePasswords;
        Charset[] additionalPasswordEncodings;
        {
            String keystorePasswordSpec =
                    (this.keystorePasswordSpec != null)
                            ? this.keystorePasswordSpec
                            : PasswordRetriever.SPEC_STDIN;
            additionalPasswordEncodings =
                    (passwordCharset != null) ? new Charset[] {passwordCharset} : new Charset[0];
            keystorePasswords =
                    passwordRetriever.getPasswords(keystorePasswordSpec,
                            "Keystore password for " + name, additionalPasswordEncodings);
            loadKeyStoreFromFile(
                    ks, "NONE".equals(keystoreFile) ? null : keystoreFile, keystorePasswords);
        }

        // 3. Load the PrivateKey and cert chain from KeyStore
        String keyAlias = null;
        PrivateKey key = null;
        try {
            if (keystoreKeyAlias == null) {
                // Private key entry alias not specified. Find the key entry contained in this
                // KeyStore. If the KeyStore contains multiple key entries, return an error.
                Enumeration<String> aliases = ks.aliases();
                if (aliases != null) {
                    while (aliases.hasMoreElements()) {
                        String entryAlias = aliases.nextElement();
                        if (ks.isKeyEntry(entryAlias)) {
                            keyAlias = entryAlias;
                            if (keystoreKeyAlias != null) {
                                throw new ParameterException(
                                        keystoreFile
                                                + " contains multiple key entries"
                                                + ". --ks-key-alias option must be used to specify"
                                                + " which entry to use.");
                            }
                            keystoreKeyAlias = keyAlias;
                        }
                    }
                }
                if (keystoreKeyAlias == null) {
                    throw new ParameterException(keystoreFile + " does not contain key entries");
                }
            }

            // Private key entry alias known. Load that entry's private key.
            keyAlias = keystoreKeyAlias;
            if (!ks.isKeyEntry(keyAlias)) {
                throw new ParameterException(
                        keystoreFile + " entry \"" + keyAlias + "\" does not contain a key");
            }

            Key entryKey;
            if (keyPasswordSpec != null) {
                // Key password spec is explicitly specified. Use this spec to obtain the
                // password and then load the key using that password.
                List<char[]> keyPasswords =
                        passwordRetriever.getPasswords(
                                keyPasswordSpec,
                                "Key \"" + keyAlias + "\" password for " + name,
                                additionalPasswordEncodings);
                entryKey = getKeyStoreKey(ks, keyAlias, keyPasswords);
            } else {
                // Key password spec is not specified. This means we should assume that key
                // password is the same as the keystore password and that, if this assumption is
                // wrong, we should prompt for key password and retry loading the key using that
                // password.
                try {
                    entryKey = getKeyStoreKey(ks, keyAlias, keystorePasswords);
                } catch (UnrecoverableKeyException expected) {
                    List<char[]> keyPasswords =
                            passwordRetriever.getPasswords(
                                    PasswordRetriever.SPEC_STDIN,
                                    "Key \"" + keyAlias + "\" password for " + name,
                                    additionalPasswordEncodings);
                    entryKey = getKeyStoreKey(ks, keyAlias, keyPasswords);
                }
            }

            if (entryKey == null) {
                throw new ParameterException(
                        keystoreFile + " entry \"" + keyAlias + "\" does not contain a key");
            } else if (!(entryKey instanceof PrivateKey)) {
                throw new ParameterException(
                        keystoreFile
                                + " entry \""
                                + keyAlias
                                + "\" does not contain a private"
                                + " key. It contains a key of algorithm: "
                                + entryKey.getAlgorithm());
            }
            key = (PrivateKey) entryKey;
        } catch (UnrecoverableKeyException e) {
            throw new IOException(
                    "Failed to obtain key with alias \""
                            + keyAlias
                            + "\" from "
                            + keystoreFile
                            + ". Wrong password?",
                    e);
        }
        this.privateKey = key;
        Certificate[] certChain = ks.getCertificateChain(keyAlias);
        if ((certChain == null) || (certChain.length == 0)) {
            throw new ParameterException(
                    keystoreFile + " entry \"" + keyAlias + "\" does not contain certificates");
        }
        this.certs = new ArrayList<>(certChain.length);
        for (Certificate cert : certChain) {
            this.certs.add((X509Certificate) cert);
        }
    }

    /**
     * Loads the password-protected keystore from storage.
     *
     * @param file file backing the keystore or {@code null} if the keystore is not file-backed, for
     *     example, a PKCS #11 KeyStore.
     */
    private static void loadKeyStoreFromFile(KeyStore ks, String file, List<char[]> passwords)
            throws Exception {
        Exception lastFailure = null;
        for (char[] password : passwords) {
            try {
                if (file != null) {
                    try (FileInputStream in = new FileInputStream(file)) {
                        ks.load(in, password);
                    }
                } else {
                    ks.load(null, password);
                }
                return;
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        if (lastFailure == null) {
            throw new RuntimeException("No keystore passwords");
        } else {
            throw lastFailure;
        }
    }

    private static Key getKeyStoreKey(KeyStore ks, String keyAlias, List<char[]> passwords)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        UnrecoverableKeyException lastFailure = null;
        for (char[] password : passwords) {
            try {
                return ks.getKey(keyAlias, password);
            } catch (UnrecoverableKeyException e) {
                lastFailure = e;
            }
        }
        if (lastFailure == null) {
            throw new RuntimeException("No key passwords");
        } else {
            throw lastFailure;
        }
    }

    private void loadPrivateKeyAndCertsFromFiles(PasswordRetriever passwordRetriever)
            throws Exception {
        if (keyFile == null) {
            throw new ParameterException("Private key file (--key) must be specified");
        }
        if (certFile == null) {
            throw new ParameterException("Certificate file (--cert) must be specified");
        }
        byte[] privateKeyBlob = readFully(new File(keyFile));

        PKCS8EncodedKeySpec keySpec;
        // Potentially encrypted key blob
        try {
            EncryptedPrivateKeyInfo encryptedPrivateKeyInfo =
                    new EncryptedPrivateKeyInfo(privateKeyBlob);

            // The blob is indeed an encrypted private key blob
            String passwordSpec =
                    (keyPasswordSpec != null) ? keyPasswordSpec : PasswordRetriever.SPEC_STDIN;
            Charset[] additionalPasswordEncodings =
                    (passwordCharset != null) ? new Charset[] {passwordCharset} : new Charset[0];
            List<char[]> keyPasswords =
                    passwordRetriever.getPasswords(
                            passwordSpec, "Private key password for " + name,
                            additionalPasswordEncodings);
            keySpec = decryptPkcs8EncodedKey(encryptedPrivateKeyInfo, keyPasswords);
        } catch (IOException e) {
            // The blob is not an encrypted private key blob
            if (keyPasswordSpec == null) {
                // Given that no password was specified, assume the blob is an unencrypted
                // private key blob
                keySpec = new PKCS8EncodedKeySpec(privateKeyBlob);
            } else {
                throw new InvalidKeySpecException(
                        "Failed to parse encrypted private key blob " + keyFile, e);
            }
        }

        // Load the private key from its PKCS #8 encoded form.
        try {
            privateKey = loadPkcs8EncodedPrivateKey(keySpec);
        } catch (InvalidKeySpecException e) {
            throw new InvalidKeySpecException(
                    "Failed to load PKCS #8 encoded private key from " + keyFile, e);
        }

        // Load certificates
        Collection<? extends Certificate> certs;
        try (FileInputStream in = new FileInputStream(certFile)) {
            certs = X509CertificateUtils.generateCertificates(in);
        }
        List<X509Certificate> certList = new ArrayList<>(certs.size());
        for (Certificate cert : certs) {
            certList.add((X509Certificate) cert);
        }
        this.certs = certList;
    }

    private static PKCS8EncodedKeySpec decryptPkcs8EncodedKey(
            EncryptedPrivateKeyInfo encryptedPrivateKeyInfo, List<char[]> passwords)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        SecretKeyFactory keyFactory =
                SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
        InvalidKeySpecException lastKeySpecException = null;
        InvalidKeyException lastKeyException = null;
        for (char[] password : passwords) {
            PBEKeySpec decryptionKeySpec = new PBEKeySpec(password);
            try {
                SecretKey decryptionKey = keyFactory.generateSecret(decryptionKeySpec);
                return encryptedPrivateKeyInfo.getKeySpec(decryptionKey);
            } catch (InvalidKeySpecException e) {
                lastKeySpecException = e;
            } catch (InvalidKeyException e) {
                lastKeyException = e;
            }
        }
        if ((lastKeyException == null) && (lastKeySpecException == null)) {
            throw new RuntimeException("No passwords");
        } else if (lastKeyException != null) {
            throw lastKeyException;
        } else {
            throw lastKeySpecException;
        }
    }

    private static PrivateKey loadPkcs8EncodedPrivateKey(PKCS8EncodedKeySpec spec)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (InvalidKeySpecException expected) {
        }
        try {
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        } catch (InvalidKeySpecException expected) {
        }
        try {
            return KeyFactory.getInstance("DSA").generatePrivate(spec);
        } catch (InvalidKeySpecException expected) {
        }
        throw new InvalidKeySpecException("Not an RSA, EC, or DSA private key");
    }

    private static byte[] readFully(File file) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(file)) {
            drain(in, result);
        }
        return result.toByteArray();
    }

    private static void drain(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int chunkSize;
        while ((chunkSize = in.read(buf)) != -1) {
            out.write(buf, 0, chunkSize);
        }
    }
}
