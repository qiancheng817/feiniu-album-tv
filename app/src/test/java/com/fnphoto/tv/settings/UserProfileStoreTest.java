package com.fnphoto.tv.settings;

import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UserProfileStoreTest {
    @Test
    public void switchToProfile_publishesSessionBeforeReturning() throws GeneralSecurityException {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        UserProfileStore store = new UserProfileStore(prefs);
        saveProfile(store, prefs, "alice", "token-a");
        saveProfile(store, prefs, "bob", "token-b");
        String aliceId = profileIdFor(store, "alice");

        assertTrue(store.switchToProfile(aliceId));

        assertEquals(aliceId, prefs.getString(UserProfileStore.PREF_ACTIVE_PROFILE_ID, ""));
        assertEquals("token-a", prefs.getString("api_token", ""));
    }

    @Test
    public void persistCurrentSessionToActiveProfile_keepsProfileTokenWhenGlobalTokenIsBlank()
            throws GeneralSecurityException {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        UserProfileStore store = new UserProfileStore(prefs);
        saveProfile(store, prefs, "alice", "token-a");
        String aliceId = profileIdFor(store, "alice");
        assertEquals(aliceId, prefs.getString(UserProfileStore.PREF_ACTIVE_PROFILE_ID, ""));

        prefs.edit().putString("api_token", "").putString("secret", "").putString("backId", "").commit();

        store.persistCurrentSessionToActiveProfile();
        prefs.flushApplied();

        UserProfileStore.ProfileSummary alice = profileFor(store, "alice");
        assertTrue(alice.hasSession());
    }

    @Test
    public void browsePosition_roundTripsTagPhotoSource() throws GeneralSecurityException {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        UserProfileStore store = new UserProfileStore(prefs);
        saveProfile(store, prefs, "alice", "token-a");

        store.saveActiveBrowsePosition(UserProfileStore.BrowsePosition.photo(
                "tags",
                "tag",
                "raw server tag",
                "",
                "Display tag",
                "photo-7",
                4
        ));
        prefs.flushApplied();

        UserProfileStore.BrowsePosition restored = store.getActiveBrowsePosition();
        assertEquals("photo", restored.kind);
        assertEquals("tags", restored.action);
        assertEquals("tag", restored.sourceType);
        assertEquals("raw server tag", restored.sourceId);
        assertEquals("", restored.sourcePayload);
        assertEquals("Display tag", restored.sourceTitle);
        assertEquals("photo-7", restored.itemId);
        assertEquals(4, restored.gridIndex);
    }

    @Test
    public void browsePosition_roundTripsMediaTypePhotoSource() throws GeneralSecurityException {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        UserProfileStore store = new UserProfileStore(prefs);
        saveProfile(store, prefs, "alice", "token-a");

        store.saveActiveBrowsePosition(UserProfileStore.BrowsePosition.photo(
                "media_types",
                "media_type",
                "2",
                "video",
                "视频",
                "photo-9",
                6
        ));
        prefs.flushApplied();

        UserProfileStore.BrowsePosition restored = store.getActiveBrowsePosition();
        assertEquals("photo", restored.kind);
        assertEquals("media_types", restored.action);
        assertEquals("media_type", restored.sourceType);
        assertEquals("2", restored.sourceId);
        assertEquals("video", restored.sourcePayload);
        assertEquals("视频", restored.sourceTitle);
        assertEquals("photo-9", restored.itemId);
        assertEquals(6, restored.gridIndex);
    }

    @Test
    public void browsePosition_legacyJsonKeepsPhotoFields() throws GeneralSecurityException {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        UserProfileStore store = new UserProfileStore(prefs);
        saveProfile(store, prefs, "alice", "token-a");
        String profileId = profileIdFor(store, "alice");
        prefs.edit().putString(
                "profile." + profileId + "." + UserProfileStore.PREF_BROWSE_POSITION,
                "{\"kind\":\"photo\",\"action\":\"tags\",\"sourceType\":\"tag\",\"sourceId\":\"raw tag\",\"sourcePayload\":\"\",\"sourceTitle\":\"Tag\",\"itemType\":\"photo\",\"itemId\":\"photo-1\",\"gridIndex\":3}"
        ).commit();

        UserProfileStore.BrowsePosition restored = store.getActiveBrowsePosition();
        assertEquals("photo", restored.kind);
        assertEquals("tags", restored.action);
        assertEquals("tag", restored.sourceType);
        assertEquals("raw tag", restored.sourceId);
        assertEquals("Tag", restored.sourceTitle);
        assertEquals("photo", restored.itemType);
        assertEquals("photo-1", restored.itemId);
        assertEquals(3, restored.gridIndex);
    }

    @Test
    public void browsePosition_legacyJsonKeepsCollectionFields() throws GeneralSecurityException {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        UserProfileStore store = new UserProfileStore(prefs);
        saveProfile(store, prefs, "alice", "token-a");
        String profileId = profileIdFor(store, "alice");
        prefs.edit().putString(
                "profile." + profileId + "." + UserProfileStore.PREF_BROWSE_POSITION,
                "{\"kind\":\"collection\",\"action\":\"media_types\",\"sourceType\":\"\",\"sourceId\":\"\",\"sourcePayload\":\"\",\"sourceTitle\":\"\",\"itemType\":\"media_type\",\"itemId\":\"media:2\",\"gridIndex\":5}"
        ).commit();

        UserProfileStore.BrowsePosition restored = store.getActiveBrowsePosition();
        assertEquals("collection", restored.kind);
        assertEquals("media_types", restored.action);
        assertEquals("media_type", restored.itemType);
        assertEquals("media:2", restored.itemId);
        assertEquals(5, restored.gridIndex);
    }

    private static void saveProfile(
            UserProfileStore store,
            InMemorySharedPreferences prefs,
            String user,
            String token
    ) throws GeneralSecurityException {
        store.saveAccountSession("http://photos.local", user, "pass-" + user, token, "secret-" + user, "back-" + user, true);
        prefs.flushApplied();
    }

    private static String profileIdFor(UserProfileStore store, String displayName) {
        return profileFor(store, displayName).getId();
    }

    private static UserProfileStore.ProfileSummary profileFor(UserProfileStore store, String displayName) {
        List<UserProfileStore.ProfileSummary> profiles = store.listProfiles();
        for (UserProfileStore.ProfileSummary profile : profiles) {
            if (displayName.equals(profile.getDisplayName())) {
                return profile;
            }
        }
        throw new AssertionError("Missing profile " + displayName);
    }
}
