package com.fnphoto.tv.settings;

import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SessionPreferences {
    public static final String PREFS_NAME = "fn_photo_prefs";
    public static final List<String> LOGOUT_KEYS = Collections.unmodifiableList(Arrays.asList(
            "nas_url",
            "api_token",
            "secret",
            "backId",
            "has_credentials",
            "saved_url",
            "saved_user",
            "saved_pass",
            "saved_user_enc",
            "saved_pass_enc",
            "credential_aes_key",
            "active_profile_id",
            "profile_ids"
    ));
    public static final List<String> EXPIRED_SESSION_KEYS = Collections.unmodifiableList(Arrays.asList(
            "api_token",
            "secret",
            "backId",
            "has_credentials"
    ));

    private SessionPreferences() {
    }

    public static SharedPreferences.Editor removeLogoutKeys(SharedPreferences.Editor editor) {
        for (String key : LOGOUT_KEYS) {
            editor.remove(key);
        }
        return editor;
    }

    public static SharedPreferences.Editor removeExpiredSessionKeys(SharedPreferences.Editor editor) {
        for (String key : EXPIRED_SESSION_KEYS) {
            editor.remove(key);
        }
        return editor;
    }
}
