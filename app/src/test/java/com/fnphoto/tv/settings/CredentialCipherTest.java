package com.fnphoto.tv.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class CredentialCipherTest {
    @Test
    public void encryptedTextDoesNotContainPlaintextAndCanBeDecrypted() throws Exception {
        String key = CredentialCipher.generateKey();
        String encrypted = CredentialCipher.encrypt("alice@example.test", key);

        assertFalse(encrypted.contains("alice"));
        assertEquals("alice@example.test", CredentialCipher.decrypt(encrypted, key));
    }

    @Test
    public void encryptUsesRandomIvForEachValue() throws Exception {
        String key = CredentialCipher.generateKey();

        String first = CredentialCipher.encrypt("same-password", key);
        String second = CredentialCipher.encrypt("same-password", key);

        assertNotEquals(first, second);
        assertEquals("same-password", CredentialCipher.decrypt(first, key));
        assertEquals("same-password", CredentialCipher.decrypt(second, key));
    }
}
