package com.fnphoto.tv.ui;

import org.junit.Test;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TvNavigationTest {
    @Test
    public void primaryItems_matchFirstReleaseOrder() {
        List<TvNavItem> items = TvNavigation.primaryItems();

        assertEquals(9, items.size());
        assertEquals("图库", items.get(0).title);
        assertEquals("gallery", items.get(0).action);
        assertEquals("recent", items.get(1).action);
        assertEquals("favorites", items.get(2).action);
        assertEquals("albums", items.get(3).action);
        assertEquals("folders", items.get(4).action);
        assertEquals("people", items.get(5).action);
        assertEquals("places", items.get(6).action);
        assertEquals("search", items.get(7).action);
        assertEquals("settings", items.get(8).action);
    }

    @Test
    public void homeStreamTopItems_matchWebFeatureListAndExposeImplementedEntrypoints() {
        List<TvNavItem> items = TvNavigation.homeStreamTopItems();

        assertEquals(
            Arrays.asList(
                "gallery", "albums", "folders", "favorites", "recent", "shared",
                "people", "places", "smart", "tags", "media_types"
            ),
            items.stream().map(item -> item.action).collect(Collectors.toList())
        );
        assertTrue(items.stream().allMatch(item -> item.enabled));
    }

    @Test
    public void homeStreamTopItems_areValueEqualAcrossBuilds() {
        assertEquals(TvNavigation.homeStreamTopItems(), TvNavigation.homeStreamTopItems());
    }
}
