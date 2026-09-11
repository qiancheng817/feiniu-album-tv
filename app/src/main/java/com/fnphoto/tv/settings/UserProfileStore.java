package com.fnphoto.tv.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UserProfileStore {
    private static final String TAG = "UserProfileStore";

    public static final String PREF_ACTIVE_PROFILE_ID = "active_profile_id";
    public static final String PREF_PROFILE_IDS = "profile_ids";
    public static final String PREF_CREDENTIAL_AES_KEY = "credential_aes_key";
    public static final String PREF_SAVED_USER_ENC = "saved_user_enc";
    public static final String PREF_SAVED_PASS_ENC = "saved_pass_enc";
    public static final String PREF_REMEMBER_BROWSE_POSITION = "remember_browse_position";
    public static final String PREF_BROWSE_POSITION = "browse_position";

    private static final String PROFILE_PREFIX = "profile.";
    private static final String KEY_NAS_URL = "nas_url";
    private static final String KEY_API_TOKEN = "api_token";
    private static final String KEY_SECRET = "secret";
    private static final String KEY_BACK_ID = "backId";
    private static final String KEY_HAS_CREDENTIALS = "has_credentials";
    private static final String KEY_SAVED_URL = "saved_url";
    private static final String KEY_SAVED_USER = "saved_user";
    private static final String KEY_SAVED_PASS = "saved_pass";

    private final SharedPreferences prefs;

    public UserProfileStore(Context context) {
        this.prefs = context.getSharedPreferences(SessionPreferences.PREFS_NAME, Context.MODE_PRIVATE);
    }

    UserProfileStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public void ensureActiveProfileFromCurrentSession() {
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        String url = prefs.getString(KEY_NAS_URL, "");
        String token = prefs.getString(KEY_API_TOKEN, "");
        if (!activeId.isEmpty() || url.isEmpty() || token.isEmpty()) {
            return;
        }

        String userForId = "session";
        RememberedCredentials credentials = getGlobalRememberedCredentials();
        if (credentials != null && !credentials.user.isEmpty()) {
            userForId = credentials.user;
        }
        String profileId = profileIdFor(url, userForId);
        SharedPreferences.Editor editor = prefs.edit();
        addProfileId(editor, profileId);
        putProfileSession(editor, profileId, url, token, prefs.getString(KEY_SECRET, ""), prefs.getString(KEY_BACK_ID, ""));
        if (credentials != null) {
            try {
                String key = getOrCreateCredentialKey(editor);
                putProfileCredentials(editor, profileId, credentials.user, credentials.pass, key);
                putGlobalCredentials(editor, url, credentials.user, credentials.pass, key);
            } catch (GeneralSecurityException e) {
                Log.w(TAG, "Unable to migrate remembered credentials into profile", e);
            }
        }
        commit(editor.putString(PREF_ACTIVE_PROFILE_ID, profileId), "ensure active profile");
    }

    public void saveAccountSession(
            String url,
            String user,
            String pass,
            String token,
            String secret,
            String backId,
            boolean rememberCredentials
    ) throws GeneralSecurityException {
        String profileId = profileIdFor(url, user);
        SharedPreferences.Editor editor = prefs.edit();
        addProfileId(editor, profileId);
        putProfileSession(editor, profileId, url, token, secret, backId);
        editor.putBoolean(profileKey(profileId, KEY_HAS_CREDENTIALS), rememberCredentials);
        editor.putString(PREF_ACTIVE_PROFILE_ID, profileId);
        putGlobalSession(editor, url, token, secret, backId);

        String key = getOrCreateCredentialKey(editor);
        editor.putString(profileKey(profileId, PREF_SAVED_USER_ENC), CredentialCipher.encrypt(user, key));
        if (rememberCredentials) {
            editor.putString(profileKey(profileId, PREF_SAVED_PASS_ENC), CredentialCipher.encrypt(pass, key));
            putGlobalCredentials(editor, url, user, pass, key);
        } else {
            editor.remove(profileKey(profileId, PREF_SAVED_PASS_ENC));
            clearGlobalCredentials(editor, url);
        }
        commit(editor, "save account session");
    }

    public void saveQrSession(String url, String token, String secret, String backId) {
        String profileId = profileIdFor(url, "qr");
        SharedPreferences.Editor editor = prefs.edit();
        addProfileId(editor, profileId);
        putProfileSession(editor, profileId, url, token, secret, backId);
        editor.putBoolean(profileKey(profileId, KEY_HAS_CREDENTIALS), false);
        editor.remove(profileKey(profileId, PREF_SAVED_USER_ENC));
        editor.remove(profileKey(profileId, PREF_SAVED_PASS_ENC));
        editor.putString(PREF_ACTIVE_PROFILE_ID, profileId);
        putGlobalSession(editor, url, token, secret, backId);
        clearGlobalCredentials(editor, url);
        commit(editor, "save QR session");
    }

    public RememberedCredentials getActiveRememberedCredentials() {
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        if (!activeId.isEmpty()) {
            RememberedCredentials credentials = getProfileRememberedCredentials(activeId);
            if (credentials != null) {
                return credentials;
            }
        }
        return getGlobalRememberedCredentials();
    }

    public RememberedCredentials getGlobalRememberedCredentials() {
        String url = firstNonBlank(prefs.getString(KEY_SAVED_URL, ""), prefs.getString(KEY_NAS_URL, ""));
        if (url.isEmpty() || !prefs.getBoolean(KEY_HAS_CREDENTIALS, false)) {
            return null;
        }

        String encryptedUser = prefs.getString(PREF_SAVED_USER_ENC, "");
        String encryptedPass = prefs.getString(PREF_SAVED_PASS_ENC, "");
        if (!encryptedUser.isEmpty() && !encryptedPass.isEmpty()) {
            try {
                String key = prefs.getString(PREF_CREDENTIAL_AES_KEY, "");
                return new RememberedCredentials(
                        url,
                        CredentialCipher.decrypt(encryptedUser, key),
                        CredentialCipher.decrypt(encryptedPass, key)
                );
            } catch (GeneralSecurityException e) {
                Log.w(TAG, "Unable to decrypt remembered credentials", e);
                return null;
            }
        }

        String user = prefs.getString(KEY_SAVED_USER, "");
        String pass = prefs.getString(KEY_SAVED_PASS, "");
        if (user.isEmpty() || pass.isEmpty()) {
            return null;
        }
        return new RememberedCredentials(url, user, pass);
    }

    public void updateActiveSession(String token, String secret, String backId) {
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_API_TOKEN, token)
                .putString(KEY_SECRET, secret)
                .putString(KEY_BACK_ID, backId);
        if (!activeId.isEmpty()) {
            editor.putString(profileKey(activeId, KEY_API_TOKEN), token)
                    .putString(profileKey(activeId, KEY_SECRET), secret)
                    .putString(profileKey(activeId, KEY_BACK_ID), backId);
        }
        commit(editor, "update active session");
    }

    public void persistCurrentSessionToActiveProfile() {
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        if (activeId.isEmpty()) {
            ensureActiveProfileFromCurrentSession();
            return;
        }
        String url = prefs.getString(KEY_NAS_URL, "");
        if (url.isEmpty()) {
            return;
        }
        String token = prefs.getString(KEY_API_TOKEN, "");
        SharedPreferences.Editor editor = prefs.edit()
                .putString(profileKey(activeId, KEY_NAS_URL), url)
                .putString(profileKey(activeId, KEY_SAVED_URL), prefs.getString(KEY_SAVED_URL, url));
        if (!token.isEmpty()) {
            editor.putString(profileKey(activeId, KEY_API_TOKEN), token)
                    .putString(profileKey(activeId, KEY_SECRET), prefs.getString(KEY_SECRET, ""))
                    .putString(profileKey(activeId, KEY_BACK_ID), prefs.getString(KEY_BACK_ID, ""));
        }
        commit(editor, "persist current session");
    }

    public boolean switchToProfile(String profileId) {
        if (profileId == null || profileId.isEmpty() || !profileIds().contains(profileId)) {
            return false;
        }
        persistCurrentSessionToActiveProfile();

        String url = prefs.getString(profileKey(profileId, KEY_NAS_URL), "");
        String token = prefs.getString(profileKey(profileId, KEY_API_TOKEN), "");
        if (url.isEmpty() || token.isEmpty()) {
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_ACTIVE_PROFILE_ID, profileId);
        putGlobalSession(
                editor,
                url,
                token,
                prefs.getString(profileKey(profileId, KEY_SECRET), ""),
                prefs.getString(profileKey(profileId, KEY_BACK_ID), "")
        );
        if (prefs.getBoolean(profileKey(profileId, KEY_HAS_CREDENTIALS), false)) {
            editor.putBoolean(KEY_HAS_CREDENTIALS, true)
                    .putString(KEY_SAVED_URL, url)
                    .putString(PREF_SAVED_USER_ENC, prefs.getString(profileKey(profileId, PREF_SAVED_USER_ENC), ""))
                    .putString(PREF_SAVED_PASS_ENC, prefs.getString(profileKey(profileId, PREF_SAVED_PASS_ENC), ""))
                    .remove(KEY_SAVED_USER)
                    .remove(KEY_SAVED_PASS);
        } else {
            clearGlobalCredentials(editor, url);
        }
        return commit(editor, "switch profile");
    }

    public List<ProfileSummary> listProfiles() {
        List<String> ids = profileIds();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        List<ProfileSummary> profiles = new ArrayList<>();
        for (String id : ids) {
            String url = prefs.getString(profileKey(id, KEY_NAS_URL), "");
            if (url.isEmpty()) {
                continue;
            }
            boolean hasCredentials = prefs.getBoolean(profileKey(id, KEY_HAS_CREDENTIALS), false);
            boolean hasSession = !prefs.getString(profileKey(id, KEY_API_TOKEN), "").isEmpty();
            profiles.add(new ProfileSummary(
                    id,
                    profileDisplayName(id, hasCredentials),
                    url,
                    id.equals(activeId),
                    hasCredentials,
                    hasSession
            ));
        }
        return profiles;
    }

    public String getActiveProfileTitle() {
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        for (ProfileSummary profile : listProfiles()) {
            if (profile.id.equals(activeId)) {
                return profile.displayName;
            }
        }
        return "当前用户";
    }

    public boolean isRememberBrowsePositionEnabled() {
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        return !activeId.isEmpty() && prefs.getBoolean(profileKey(activeId, PREF_REMEMBER_BROWSE_POSITION), false);
    }

    public void setRememberBrowsePositionEnabled(boolean enabled) {
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        if (activeId.isEmpty()) {
            ensureActiveProfileFromCurrentSession();
            activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        }
        if (!activeId.isEmpty()) {
            prefs.edit().putBoolean(profileKey(activeId, PREF_REMEMBER_BROWSE_POSITION), enabled).apply();
        }
    }

    public void saveActiveBrowsePosition(BrowsePosition position) {
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        if (activeId.isEmpty() || position == null) {
            return;
        }
        prefs.edit().putString(profileKey(activeId, PREF_BROWSE_POSITION), position.toJson().toString()).apply();
    }

    public BrowsePosition getActiveBrowsePosition() {
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        if (activeId.isEmpty()) {
            return null;
        }
        String raw = prefs.getString(profileKey(activeId, PREF_BROWSE_POSITION), "");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return BrowsePosition.fromJson(new JSONObject(raw));
        } catch (Exception e) {
            Log.w(TAG, "Unable to parse browse position", e);
            return null;
        }
    }

    public void clearAll() {
        List<String> ids = profileIds();
        String activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, "");
        if (!activeId.isEmpty() && !ids.contains(activeId)) {
            ids.add(activeId);
        }

        SharedPreferences.Editor editor = SessionPreferences.removeLogoutKeys(prefs.edit());
        for (String id : ids) {
            for (String suffix : profileSuffixes()) {
                editor.remove(profileKey(id, suffix));
            }
        }
        commit(editor, "clear profiles");
    }

    private RememberedCredentials getProfileRememberedCredentials(String profileId) {
        if (!prefs.getBoolean(profileKey(profileId, KEY_HAS_CREDENTIALS), false)) {
            return null;
        }
        String url = prefs.getString(profileKey(profileId, KEY_NAS_URL), "");
        String encryptedUser = prefs.getString(profileKey(profileId, PREF_SAVED_USER_ENC), "");
        String encryptedPass = prefs.getString(profileKey(profileId, PREF_SAVED_PASS_ENC), "");
        if (url.isEmpty() || encryptedUser.isEmpty() || encryptedPass.isEmpty()) {
            return null;
        }
        try {
            String key = prefs.getString(PREF_CREDENTIAL_AES_KEY, "");
            return new RememberedCredentials(
                    url,
                    CredentialCipher.decrypt(encryptedUser, key),
                    CredentialCipher.decrypt(encryptedPass, key)
            );
        } catch (GeneralSecurityException e) {
            Log.w(TAG, "Unable to decrypt profile credentials", e);
            return null;
        }
    }

    private String profileDisplayName(String profileId, boolean hasCredentials) {
        String encryptedUser = prefs.getString(profileKey(profileId, PREF_SAVED_USER_ENC), "");
        if (!encryptedUser.isEmpty()) {
            try {
                String key = prefs.getString(PREF_CREDENTIAL_AES_KEY, "");
                String user = CredentialCipher.decrypt(encryptedUser, key);
                if (!user.isEmpty()) {
                    return user;
                }
            } catch (GeneralSecurityException e) {
                Log.w(TAG, "Unable to decrypt profile user", e);
            }
        }
        return hasCredentials ? "已保存用户" : "扫码登录用户";
    }

    private String getOrCreateCredentialKey(SharedPreferences.Editor editor) {
        String key = prefs.getString(PREF_CREDENTIAL_AES_KEY, "");
        if (!key.isEmpty()) {
            return key;
        }
        key = CredentialCipher.generateKey();
        editor.putString(PREF_CREDENTIAL_AES_KEY, key);
        return key;
    }

    private void putGlobalSession(SharedPreferences.Editor editor, String url, String token, String secret, String backId) {
        editor.putString(KEY_NAS_URL, url)
                .putString(KEY_SAVED_URL, url)
                .putString(KEY_API_TOKEN, token)
                .putString(KEY_SECRET, secret)
                .putString(KEY_BACK_ID, backId);
    }

    private void putProfileSession(
            SharedPreferences.Editor editor,
            String profileId,
            String url,
            String token,
            String secret,
            String backId
    ) {
        editor.putString(profileKey(profileId, KEY_NAS_URL), url)
                .putString(profileKey(profileId, KEY_SAVED_URL), url)
                .putString(profileKey(profileId, KEY_API_TOKEN), token)
                .putString(profileKey(profileId, KEY_SECRET), secret)
                .putString(profileKey(profileId, KEY_BACK_ID), backId);
    }

    private void putProfileCredentials(
            SharedPreferences.Editor editor,
            String profileId,
            String user,
            String pass,
            String key
    ) throws GeneralSecurityException {
        editor.putBoolean(profileKey(profileId, KEY_HAS_CREDENTIALS), true)
                .putString(profileKey(profileId, PREF_SAVED_USER_ENC), CredentialCipher.encrypt(user, key))
                .putString(profileKey(profileId, PREF_SAVED_PASS_ENC), CredentialCipher.encrypt(pass, key));
    }

    private void putGlobalCredentials(
            SharedPreferences.Editor editor,
            String url,
            String user,
            String pass,
            String key
    ) throws GeneralSecurityException {
        editor.putBoolean(KEY_HAS_CREDENTIALS, true)
                .putString(KEY_SAVED_URL, url)
                .putString(PREF_SAVED_USER_ENC, CredentialCipher.encrypt(user, key))
                .putString(PREF_SAVED_PASS_ENC, CredentialCipher.encrypt(pass, key))
                .remove(KEY_SAVED_USER)
                .remove(KEY_SAVED_PASS);
    }

    private void clearGlobalCredentials(SharedPreferences.Editor editor, String url) {
        editor.putBoolean(KEY_HAS_CREDENTIALS, false)
                .putString(KEY_SAVED_URL, url)
                .remove(PREF_SAVED_USER_ENC)
                .remove(PREF_SAVED_PASS_ENC)
                .remove(KEY_SAVED_USER)
                .remove(KEY_SAVED_PASS);
    }

    private List<String> profileIds() {
        String raw = prefs.getString(PREF_PROFILE_IDS, "[]");
        List<String> ids = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "");
                if (!id.isEmpty() && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to parse profile ids", e);
        }
        return ids;
    }

    private void addProfileId(SharedPreferences.Editor editor, String profileId) {
        List<String> ids = profileIds();
        ids.remove(profileId);
        ids.add(0, profileId);
        JSONArray array = new JSONArray();
        for (String id : ids) {
            array.put(id);
        }
        editor.putString(PREF_PROFILE_IDS, array.toString());
    }

    private static String profileKey(String profileId, String suffix) {
        return PROFILE_PREFIX + profileId + "." + suffix;
    }

    private static List<String> profileSuffixes() {
        List<String> suffixes = new ArrayList<>();
        suffixes.add(KEY_NAS_URL);
        suffixes.add(KEY_API_TOKEN);
        suffixes.add(KEY_SECRET);
        suffixes.add(KEY_BACK_ID);
        suffixes.add(KEY_HAS_CREDENTIALS);
        suffixes.add(KEY_SAVED_URL);
        suffixes.add(PREF_SAVED_USER_ENC);
        suffixes.add(PREF_SAVED_PASS_ENC);
        suffixes.add(PREF_REMEMBER_BROWSE_POSITION);
        suffixes.add(PREF_BROWSE_POSITION);
        return suffixes;
    }

    private static String profileIdFor(String url, String user) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((url.trim().toLowerCase() + "\n" + user.trim().toLowerCase()).getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString((url + "\n" + user).hashCode());
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : (second != null ? second : "");
    }

    private static boolean commit(SharedPreferences.Editor editor, String operation) {
        boolean committed = editor.commit();
        if (!committed) {
            Log.w(TAG, "Unable to commit " + operation);
        }
        return committed;
    }

    public static final class RememberedCredentials {
        public final String url;
        public final String user;
        public final String pass;

        public RememberedCredentials(String url, String user, String pass) {
            this.url = url;
            this.user = user;
            this.pass = pass;
        }
    }

    public static final class ProfileSummary {
        private final String id;
        private final String displayName;
        private final String serverUrl;
        private final boolean active;
        private final boolean hasCredentials;
        private final boolean hasSession;

        private ProfileSummary(
                String id,
                String displayName,
                String serverUrl,
                boolean active,
                boolean hasCredentials,
                boolean hasSession
        ) {
            this.id = id;
            this.displayName = displayName;
            this.serverUrl = serverUrl;
            this.active = active;
            this.hasCredentials = hasCredentials;
            this.hasSession = hasSession;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getServerUrl() {
            return serverUrl;
        }

        public boolean isActive() {
            return active;
        }

        public boolean hasCredentials() {
            return hasCredentials;
        }

        public boolean hasSession() {
            return hasSession;
        }
    }

    public static final class BrowsePosition {
        public final String kind;
        public final String action;
        public final String sourceType;
        public final String sourceId;
        public final String sourcePayload;
        public final String sourceTitle;
        public final String itemType;
        public final String itemId;
        public final int gridIndex;

        private BrowsePosition(
                String kind,
                String action,
                String sourceType,
                String sourceId,
                String sourcePayload,
                String sourceTitle,
                String itemType,
                String itemId,
                int gridIndex
        ) {
            this.kind = kind;
            this.action = action;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.sourcePayload = sourcePayload;
            this.sourceTitle = sourceTitle;
            this.itemType = itemType;
            this.itemId = itemId;
            this.gridIndex = gridIndex;
        }

        public static BrowsePosition photo(
                String action,
                String sourceType,
                String sourceId,
                String sourcePayload,
                String sourceTitle,
                String photoId,
                int gridIndex
        ) {
            return new BrowsePosition(
                    "photo",
                    action,
                    sourceType,
                    sourceId,
                    sourcePayload,
                    sourceTitle,
                    "photo",
                    photoId,
                    gridIndex
            );
        }

        public static BrowsePosition collection(String action, String itemType, String itemId, int gridIndex) {
            return new BrowsePosition(
                    "collection",
                    action,
                    "",
                    "",
                    "",
                    "",
                    itemType,
                    itemId,
                    gridIndex
            );
        }

        private JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("kind", kind);
                json.put("action", action);
                json.put("sourceType", sourceType);
                json.put("sourceId", sourceId);
                json.put("sourcePayload", sourcePayload);
                json.put("sourceTitle", sourceTitle);
                json.put("itemType", itemType);
                json.put("itemId", itemId);
                json.put("gridIndex", gridIndex);
            } catch (Exception ignored) {
            }
            return json;
        }

        private static BrowsePosition fromJson(JSONObject json) {
            return new BrowsePosition(
                    json.optString("kind", ""),
                    json.optString("action", ""),
                    json.optString("sourceType", ""),
                    json.optString("sourceId", ""),
                    json.optString("sourcePayload", ""),
                    json.optString("sourceTitle", ""),
                    json.optString("itemType", ""),
                    json.optString("itemId", ""),
                    json.optInt("gridIndex", -1)
            );
        }
    }
}
