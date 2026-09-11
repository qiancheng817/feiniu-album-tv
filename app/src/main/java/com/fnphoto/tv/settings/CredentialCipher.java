package com.fnphoto.tv.settings;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CredentialCipher {
    private static final String VERSION = "v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialCipher() {
    }

    public static String generateKey() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return Base64.encodeToString(key, Base64.NO_WRAP);
    }

    public static String encrypt(String plainText, String encodedKey) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(encodedKey), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        return VERSION
                + ":"
                + Base64.encodeToString(iv, Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    public static String decrypt(String encodedValue, String encodedKey) throws GeneralSecurityException {
        if (encodedValue == null || encodedValue.trim().isEmpty()) {
            return "";
        }

        String[] parts = encodedValue.split(":", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new GeneralSecurityException("Unsupported encrypted credential format");
        }

        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[2], Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec(encodedKey), new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKeySpec keySpec(String encodedKey) throws GeneralSecurityException {
        byte[] key = Base64.decode(encodedKey, Base64.NO_WRAP);
        if (key.length != KEY_BYTES) {
            throw new GeneralSecurityException("AES key must be 256 bits");
        }
        return new SecretKeySpec(key, "AES");
    }
}
