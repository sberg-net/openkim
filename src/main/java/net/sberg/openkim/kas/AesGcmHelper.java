/*
 * Copyright 2023 sberg it-systeme GmbH
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package net.sberg.openkim.kas;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class AesGcmHelper {

    public static final String ENCRYPT_ALGO = "AES/GCM/NoPadding";
    public static final int TAG_LENGTH_BIT = 128;
    public static final int IV_LENGTH_BYTE = 12;
    public static final int AES_KEY_BIT = 256;

    public static final Charset UTF_8 = StandardCharsets.UTF_8;

    public static byte[] getRandomNonce(int numBytes) {
        byte[] nonce = new byte[numBytes];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    // AES secret key
    public static SecretKey getAESKey(int keysize) throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        //keyGen.init(keysize, SecureRandom.getInstanceStrong());
        keyGen.init(keysize);
        return keyGen.generateKey();
    }

    public static void encryptWithStream(File output, File input, SecretKey secret, byte[] iv, boolean withPrefix) throws Exception {
        FileOutputStream outputStream = new FileOutputStream(output);
        if (withPrefix) {
            outputStream.write(iv);
        }

        final GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        cipher.init(true, new AEADParameters(new KeyParameter(secret.getEncoded()), TAG_LENGTH_BIT, iv));

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(input))) {
            try (BufferedOutputStream out = new BufferedOutputStream(new CipherOutputStream(outputStream, cipher))) {
                int length = 0;
                byte[] bytes = new byte[16 * 1024];

                while ((length = in.read(bytes)) != -1) {
                    out.write(bytes, 0, length);
                }

                in.close();
                out.close();
            }
        }
    }

    // AES-GCM needs GCMParameterSpec
    public static byte[] encrypt(byte[] pText, SecretKey secret, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        byte[] encryptedText = cipher.doFinal(pText);
        return encryptedText;
    }

    // prefix IV length + IV bytes to cipher text
    public static byte[] encryptWithPrefixIV(byte[] pText, SecretKey secret, byte[] iv) throws Exception {

        byte[] cipherText = encrypt(pText, secret, iv);

        byte[] cipherTextWithIv = ByteBuffer.allocate(iv.length + cipherText.length)
            .put(iv)
            .put(cipherText)
            .array();
        return cipherTextWithIv;
    }

    public static void decryptWithStreamWithPrefixIV(File output, File input, SecretKey secret) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(input);
        byte[] iv = new byte[IV_LENGTH_BYTE];
        fileInputStream.read(iv);
        decryptWithStream(output, fileInputStream, secret, iv);
    }

    public static void decryptWithStream(File output, FileInputStream fileInputStream, SecretKey secret, byte[] iv) throws Exception {
        final GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        cipher.init(false, new AEADParameters(new KeyParameter(secret.getEncoded()), TAG_LENGTH_BIT, iv));

        try (BufferedInputStream in = new BufferedInputStream(new CipherInputStream(fileInputStream, cipher))) {
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                int length = 0;
                byte[] bytes = new byte[16 * 1024];

                while ((length = in.read(bytes)) != -1) {
                    out.write(bytes, 0, length);
                }

                in.close();
                out.close();
            }
        }
    }

    public static String decrypt(byte[] cText, SecretKey secret, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        byte[] plainText = cipher.doFinal(cText);
        return new String(plainText, UTF_8);
    }

    public static String decryptWithPrefixIV(byte[] cText, SecretKey secret) throws Exception {

        ByteBuffer bb = ByteBuffer.wrap(cText);

        byte[] iv = new byte[IV_LENGTH_BYTE];
        bb.get(iv);
        //bb.get(iv, 0, iv.length);

        byte[] cipherText = new byte[bb.remaining()];
        bb.get(cipherText);

        String plainText = decrypt(cipherText, secret, iv);
        return plainText;

    }
}
