package com.fnphoto.tv.login;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class SavedServerHistoryTest {
    @Test
    public void decode_removesBlanksAndDuplicatesKeepingFirstOccurrence() {
        assertEquals(
                Arrays.asList("http://nas-a:5666", "http://nas-b:5666"),
                SavedServerHistory.decode("http://nas-a:5666\n\nhttp://nas-b:5666\nhttp://nas-a:5666")
        );
    }

    @Test
    public void addOrPromote_putsNewestServerFirstAndRemovesOldDuplicate() {
        String encoded = SavedServerHistory.addOrPromote(
                "http://nas-a:5666\nhttp://nas-b:5666",
                "http://nas-b:5666"
        );

        assertEquals(
                Arrays.asList("http://nas-b:5666", "http://nas-a:5666"),
                SavedServerHistory.decode(encoded)
        );
    }

    @Test
    public void addOrPromote_ignoresBlankServer() {
        assertEquals(
                "http://nas-a:5666",
                SavedServerHistory.addOrPromote("http://nas-a:5666", "  ")
        );
    }
}
