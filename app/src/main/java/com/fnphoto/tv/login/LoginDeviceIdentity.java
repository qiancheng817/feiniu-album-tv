package com.fnphoto.tv.login;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class LoginDeviceIdentity {
    private static final String PREFS = "fn_photo_prefs";
    private static final String PREF_DEVICE_ID = "login_device_id";

    private LoginDeviceIdentity() {
    }

    public static String getOrCreate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = prefs.getString(PREF_DEVICE_ID, "");
        if (existing != null && !existing.trim().isEmpty()) {
            return existing;
        }

        String created = UUID.randomUUID().toString().replace("-", "");
        prefs.edit().putString(PREF_DEVICE_ID, created).apply();
        return created;
    }
}
