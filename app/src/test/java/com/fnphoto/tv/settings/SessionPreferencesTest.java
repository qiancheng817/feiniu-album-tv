package com.fnphoto.tv.settings;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionPreferencesTest {
    @Test
    public void logoutKeys_includeCredentialAndRememberedAccountState() {
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("nas_url"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("api_token"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("secret"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("backId"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("has_credentials"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("saved_url"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("saved_user"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("saved_pass"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("saved_user_enc"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("saved_pass_enc"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("credential_aes_key"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("active_profile_id"));
        assertTrue(SessionPreferences.LOGOUT_KEYS.contains("profile_ids"));
    }

    @Test
    public void expiredSessionKeys_clearSessionButKeepRememberedLoginInputs() {
        assertTrue(SessionPreferences.EXPIRED_SESSION_KEYS.contains("api_token"));
        assertTrue(SessionPreferences.EXPIRED_SESSION_KEYS.contains("secret"));
        assertTrue(SessionPreferences.EXPIRED_SESSION_KEYS.contains("backId"));
        assertTrue(SessionPreferences.EXPIRED_SESSION_KEYS.contains("has_credentials"));
        assertFalse(SessionPreferences.EXPIRED_SESSION_KEYS.contains("nas_url"));
        assertFalse(SessionPreferences.EXPIRED_SESSION_KEYS.contains("saved_url"));
        assertFalse(SessionPreferences.EXPIRED_SESSION_KEYS.contains("saved_user"));
        assertFalse(SessionPreferences.EXPIRED_SESSION_KEYS.contains("saved_pass"));
        assertFalse(SessionPreferences.EXPIRED_SESSION_KEYS.contains("saved_user_enc"));
        assertFalse(SessionPreferences.EXPIRED_SESSION_KEYS.contains("saved_pass_enc"));
        assertFalse(SessionPreferences.EXPIRED_SESSION_KEYS.contains("credential_aes_key"));
    }
}
