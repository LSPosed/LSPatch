/*
 * Copyright (C) 2016 The Android Open Source Project
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

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retriever of passwords based on password specs supported by {@code apksigner} tool.
 *
 * <p>apksigner supports retrieving multiple passwords from the same source (e.g., file, standard
 * input) which adds the need to keep some sources open across password retrievals. This class
 * addresses the need.
 *
 * <p>To use this retriever, construct a new instance, use {@link #getPasswords(String, String,
 * Charset...)} to retrieve passwords, and then invoke {@link #close()} on the instance when done,
 * enabling the instance to release any held resources.
 */
public class PasswordRetriever implements AutoCloseable {
    public static final String SPEC_STDIN = "stdin";

    /** Character encoding used by the console or {@code null} if not known. */
    private final Charset mConsoleEncoding;

    private final Map<File, InputStream> mFileInputStreams = new HashMap<>();

    private boolean mClosed;

    public PasswordRetriever() {
        mConsoleEncoding = getConsoleEncoding();
    }

    /**
     * Returns the passwords described by the provided spec. The reason there may be more than one
     * password is compatibility with {@code keytool} and {@code jarsigner} which in certain cases
     * use the form of passwords encoded using the console's character encoding or the JVM default
     * encoding.
     *
     * <p>Supported specs:
     * <ul>
     * <li><em>stdin</em> -- read password as a line from console, if available, or standard
     *     input if console is not available</li>
     * <li><em>pass:password</em> -- password specified inside the spec, starting after
     *     {@code pass:}</li>
     * <li><em>file:path</em> -- read password as a line from the specified file</li>
     * <li><em>env:name</em> -- password is in the specified environment variable</li>
     * </ul>
     *
     * <p>When the same file (including standard input) is used for providing multiple passwords,
     * the passwords are read from the file one line at a time.
     *
     * @param additionalPwdEncodings additional encodings for converting the password into KeyStore
     *        or PKCS #8 encrypted key password. These encoding are used in addition to using the
     *        password verbatim or encoded using JVM default character encoding. A useful encoding
     *        to provide is the console character encoding on Windows machines where the console
     *        may be different from the JVM default encoding. Unfortunately, there is no public API
     *        to obtain the console's character encoding.
     */
    public List<char[]> getPasswords(
            String spec, String description, Charset... additionalPwdEncodings)
                    throws IOException {
        // IMPLEMENTATION NOTE: Java KeyStore and PBEKeySpec APIs take passwords as arrays of
        // Unicode characters (char[]). Unfortunately, it appears that Sun/Oracle keytool and
        // jarsigner in some cases use passwords which are the encoded form obtained using the
        // console's character encoding. For example, if the encoding is UTF-8, keytool and
        // jarsigner will use the password which is obtained by upcasting each byte of the UTF-8
        // encoded form to char. This occurs only when the password is read from stdin/console, and
        // does not occur when the password is read from a command-line parameter.
        // There are other tools which use the Java KeyStore API correctly.
        // Thus, for each password spec, a valid password is typically one of these three:
        // * Unicode characters,
        // * characters (upcast bytes) obtained from encoding the password using the console's
        //   character encoding of the console used on the environment where the KeyStore was
        //   created,
        // * characters (upcast bytes) obtained from encoding the password using the JVM's default
        //   character encoding of the machine where the KeyStore was created.
        //
        // For a sample password "\u0061\u0062\u00a1\u00e4\u044e\u0031":
        // On Windows 10 with English US as the UI language, IBM437 is used as console encoding and
        // windows-1252 is used as the JVM default encoding:
        // * keytool -genkey -v -keystore native.jks -keyalg RSA -keysize 2048 -validity 10000
        //     -alias test
        //   generates a keystore and key which decrypt only with
        //   "\u0061\u0062\u00ad\u0084\u003f\u0031"
        // * keytool -genkey -v -keystore native.jks -keyalg RSA -keysize 2048 -validity 10000
        //     -alias test -storepass <pass here>
        //   generates a keystore and key which decrypt only with
        //   "\u0061\u0062\u00a1\u00e4\u003f\u0031"
        // On modern OSX/Linux UTF-8 is used as the console and JVM default encoding:
        // * keytool -genkey -v -keystore native.jks -keyalg RSA -keysize 2048 -validity 10000
        //     -alias test
        //   generates a keystore and key which decrypt only with
        //   "\u0061\u0062\u00c2\u00a1\u00c3\u00a4\u00d1\u008e\u0031"
        // * keytool -genkey -v -keystore native.jks -keyalg RSA -keysize 2048 -validity 10000
        //     -alias test -storepass <pass here>
        //   generates a keystore and key which decrypt only with
        //   "\u0061\u0062\u00a1\u00e4\u044e\u0031"
        //
        // We optimize for the case where the KeyStore was created on the same machine where
        // apksigner is executed. Thus, we can assume the JVM default encoding used for creating the
        // KeyStore is the same as the current JVM's default encoding. We can make a similar
        // assumption about the console's encoding. However, there is no public API for obtaining
        // the console's character encoding. Prior to Java 9, we could cheat by using Reflection API
        // to access Console.encoding field. However, in the official Java 9 JVM this field is not
        // only inaccessible, but results in warnings being spewed to stdout during access attempts.
        // As a result, we cannot auto-detect the console's encoding and thus rely on the user to
        // explicitly provide it to apksigner as a command-line parameter (and passed into this
        // method as additionalPwdEncodings), if the password is using non-ASCII characters.

        assertNotClosed();
        if (spec.startsWith("pass:")) {
            char[] pwd = spec.substring("pass:".length()).toCharArray();
            return getPasswords(pwd, additionalPwdEncodings);
        } else if (SPEC_STDIN.equals(spec)) {
            Console console = System.console();
            if (console != null) {
                // Reading from console
                char[] pwd = console.readPassword(description + ": ");
                if (pwd == null) {
                    throw new IOException("Failed to read " + description + ": console closed");
                }
                return getPasswords(pwd, additionalPwdEncodings);
            } else {
                // Console not available -- reading from standard input
                System.out.println(description + ": ");
                byte[] encodedPwd = readEncodedPassword(System.in);
                if (encodedPwd.length == 0) {
                    throw new IOException(
                            "Failed to read " + description + ": standard input closed");
                }
                // By default, textual input obtained via standard input is supposed to be decoded
                // using the in JVM default character encoding.
                return getPasswords(encodedPwd, Charset.defaultCharset(), additionalPwdEncodings);
            }
        } else if (spec.startsWith("file:")) {
            String name = spec.substring("file:".length());
            File file = new File(name).getCanonicalFile();
            InputStream in = mFileInputStreams.get(file);
            if (in == null) {
                in = new FileInputStream(file);
                mFileInputStreams.put(file, in);
            }
            byte[] encodedPwd = readEncodedPassword(in);
            if (encodedPwd.length == 0) {
                throw new IOException(
                        "Failed to read " + description + " : end of file reached in " + file);
            }
            // By default, textual input from files is supposed to be treated as encoded using JVM's
            // default character encoding.
            return getPasswords(encodedPwd, Charset.defaultCharset(), additionalPwdEncodings);
        } else if (spec.startsWith("env:")) {
            String name = spec.substring("env:".length());
            String value = System.getenv(name);
            if (value == null) {
                throw new IOException(
                        "Failed to read " + description + ": environment variable " + value
                                + " not specified");
            }
            return getPasswords(value.toCharArray(), additionalPwdEncodings);
        } else {
            throw new IOException("Unsupported password spec for " + description + ": " + spec);
        }
    }

    /**
     * Returns the provided password and all password variants derived from the password. The
     * resulting list is guaranteed to contain at least one element.
     */
    private List<char[]> getPasswords(char[] pwd, Charset... additionalEncodings) {
        List<char[]> passwords = new ArrayList<>(3);
        addPasswords(passwords, pwd, additionalEncodings);
        return passwords;
    }

    /**
     * Returns the provided password and all password variants derived from the password. The
     * resulting list is guaranteed to contain at least one element.
     *
     * @param encodedPwd password encoded using {@code encodingForDecoding}.
     */
    private List<char[]> getPasswords(
            byte[] encodedPwd, Charset encodingForDecoding,
            Charset... additionalEncodings) {
        List<char[]> passwords = new ArrayList<>(4);

        // Decode password and add it and its variants to the list
        try {
            char[] pwd = decodePassword(encodedPwd, encodingForDecoding);
            addPasswords(passwords, pwd, additionalEncodings);
        } catch (IOException ignored) {}

        // Add the original encoded form
        addPassword(passwords, castBytesToChars(encodedPwd));
        return passwords;
    }

    /**
     * Adds the provided password and its variants to the provided list of passwords.
     *
     * <p>NOTE: This method adds only the passwords/variants which are not yet in the list.
     */
    private void addPasswords(List<char[]> passwords, char[] pwd, Charset... additionalEncodings) {
        if ((additionalEncodings != null) && (additionalEncodings.length > 0)) {
            for (Charset encoding : additionalEncodings) {
                // Password encoded using provided encoding (usually the console's character
                // encoding) and upcast into char[]
                try {
                    char[] encodedPwd = castBytesToChars(encodePassword(pwd, encoding));
                    addPassword(passwords, encodedPwd);
                } catch (IOException ignored) {}
            }
        }

        // Verbatim password
        addPassword(passwords, pwd);

        // Password encoded using the console encoding and upcast into char[]
        if (mConsoleEncoding != null) {
            try {
                char[] encodedPwd = castBytesToChars(encodePassword(pwd, mConsoleEncoding));
                addPassword(passwords, encodedPwd);
            } catch (IOException ignored) {}
        }

        // Password encoded using the JVM default character encoding and upcast into char[]
        try {
            char[] encodedPwd = castBytesToChars(encodePassword(pwd, Charset.defaultCharset()));
            addPassword(passwords, encodedPwd);
        } catch (IOException ignored) {}
    }

    /**
     * Adds the provided password to the provided list. Does nothing if the password is already in
     * the list.
     */
    private static void addPassword(List<char[]> passwords, char[] password) {
        for (char[] existingPassword : passwords) {
            if (Arrays.equals(password, existingPassword)) {
                return;
            }
        }
        passwords.add(password);
    }

    private static byte[] encodePassword(char[] pwd, Charset cs) throws IOException {
        ByteBuffer pwdBytes =
                cs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .encode(CharBuffer.wrap(pwd));
        byte[] encoded = new byte[pwdBytes.remaining()];
        pwdBytes.get(encoded);
        return encoded;
    }

    private static char[] decodePassword(byte[] pwdBytes, Charset encoding) throws IOException {
        CharBuffer pwdChars =
                encoding.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(pwdBytes));
        char[] result = new char[pwdChars.remaining()];
        pwdChars.get(result);
        return result;
    }

    /**
     * Upcasts each {@code byte} in the provided array of bytes to a {@code char} and returns the
     * resulting array of characters.
     */
    private static char[] castBytesToChars(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        char[] chars = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            chars[i] = (char) (bytes[i] & 0xff);
        }
        return chars;
    }

    private static boolean isJava9OrHigherErrOnTheSideOfCaution() {
        // Before Java 9, this string is of major.minor form, such as "1.8" for Java 8.
        // From Java 9 onwards, this is a single number: major, such as "9" for Java 9.
        // See JEP 223: New Version-String Scheme.

        String versionString = System.getProperty("java.specification.version");
        if (versionString == null) {
            // Better safe than sorry
            return true;
        }
        return !versionString.startsWith("1.");
    }

    /**
     * Returns the character encoding used by the console or {@code null} if the encoding is not
     * known.
     */
    private static Charset getConsoleEncoding() {
        // IMPLEMENTATION NOTE: There is no public API for obtaining the console's character
        // encoding. We thus cheat by using implementation details of the most popular JVMs.
        // Unfortunately, this doesn't work on Java 9 JVMs where access to Console.encoding is
        // restricted by default and leads to spewing to stdout at runtime.
        if (isJava9OrHigherErrOnTheSideOfCaution()) {
            return null;
        }
        String consoleCharsetName = null;
        try {
            Method encodingMethod = Console.class.getDeclaredMethod("encoding");
            encodingMethod.setAccessible(true);
            consoleCharsetName = (String) encodingMethod.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }

        if (consoleCharsetName == null) {
            // Console encoding is the same as this JVM's default encoding
            return Charset.defaultCharset();
        }

        try {
            return getCharsetByName(consoleCharsetName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Charset getCharsetByName(String charsetName) throws IllegalArgumentException {
        // On Windows 10, cp65001 is the UTF-8 code page. For some reason, popular JVMs don't
        // have a mapping for cp65001...
        if ("cp65001".equalsIgnoreCase(charsetName)) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(charsetName);
    }

    private static byte[] readEncodedPassword(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            } else if (b == '\r') {
                int next = in.read();
                if ((next == -1) || (next == '\n')) {
                    break;
                }

                if (!(in instanceof PushbackInputStream)) {
                    in = new PushbackInputStream(in);
                }
                ((PushbackInputStream) in).unread(next);
            }
            result.write(b);
        }
        return result.toByteArray();
    }

    private void assertNotClosed() {
        if (mClosed) {
            throw new IllegalStateException("Closed");
        }
    }

    @Override
    public void close() {
        for (InputStream in : mFileInputStreams.values()) {
            try {
                in.close();
            } catch (IOException ignored) {}
        }
        mFileInputStreams.clear();
        mClosed = true;
    }
}
