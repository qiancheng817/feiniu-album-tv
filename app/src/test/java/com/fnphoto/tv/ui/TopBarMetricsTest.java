package com.fnphoto.tv.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TopBarMetricsTest {
    @Test
    public void statusClusterCentersOnTabTextBand() {
        assertEquals(72, TopBarMetrics.NAV_ITEM_HEIGHT_DP);
        assertEquals(52, TopBarMetrics.STATUS_CLUSTER_HEIGHT_DP);
        assertEquals(52, TopBarMetrics.STATUS_BUTTON_SIZE_DP);
        assertEquals(46, TopBarMetrics.STATUS_BUTTON_VISUAL_SIZE_DP);
        assertEquals(28, TopBarMetrics.THEME_ICON_SIZE_DP);
        assertEquals(30, TopBarMetrics.SETTINGS_ICON_SIZE_DP);
        assertEquals(36, TopBarMetrics.APP_LOGO_SIZE_DP);
        assertTrue(TopBarMetrics.THEME_ICON_SIZE_DP < TopBarMetrics.STATUS_BUTTON_SIZE_DP);
        assertTrue(TopBarMetrics.THEME_ICON_SIZE_DP < TopBarMetrics.SETTINGS_ICON_SIZE_DP);
        assertTrue(TopBarMetrics.STATUS_BUTTON_VISUAL_SIZE_DP < TopBarMetrics.STATUS_BUTTON_SIZE_DP);
        assertTrue(TopBarMetrics.APP_LOGO_SIZE_DP < TopBarMetrics.STATUS_CLUSTER_HEIGHT_DP);
        assertTrue(TopBarMetrics.STATUS_CLUSTER_HEIGHT_DP < TopBarMetrics.NAV_ITEM_HEIGHT_DP);
    }

    @Test
    public void statusClusterPutsThemeToggleBeforeSettings() {
        assertEquals(2, TopBarMetrics.STATUS_CLUSTER_ACTIONS.size());
        assertEquals("theme", TopBarMetrics.STATUS_CLUSTER_ACTIONS.get(0));
        assertEquals("settings", TopBarMetrics.STATUS_CLUSTER_ACTIONS.get(1));
        assertTrue(TopBarMetrics.STATUS_BUTTON_SIZE_DP == TopBarMetrics.STATUS_CLUSTER_HEIGHT_DP);
    }
}
