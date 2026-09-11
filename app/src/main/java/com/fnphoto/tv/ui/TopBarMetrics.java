package com.fnphoto.tv.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TopBarMetrics {
    public static final int NAV_ITEM_HEIGHT_DP = 72;
    public static final int STATUS_CLUSTER_HEIGHT_DP = 52;
    public static final int STATUS_BUTTON_SIZE_DP = 52;
    public static final int STATUS_BUTTON_VISUAL_SIZE_DP = 46;
    public static final int THEME_ICON_SIZE_DP = 28;
    public static final int SETTINGS_ICON_SIZE_DP = 30;
    public static final int APP_LOGO_SIZE_DP = 36;
    public static final List<String> STATUS_CLUSTER_ACTIONS = Collections.unmodifiableList(
        Arrays.asList("theme", "settings")
    );

    private TopBarMetrics() {
    }
}
