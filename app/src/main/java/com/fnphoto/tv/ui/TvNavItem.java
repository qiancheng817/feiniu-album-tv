package com.fnphoto.tv.ui;

import java.util.Objects;

public final class TvNavItem {
    public final String title;
    public final String action;
    public final boolean enabled;

    public TvNavItem(String title, String action, boolean enabled) {
        this.title = title;
        this.action = action;
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TvNavItem)) {
            return false;
        }
        TvNavItem that = (TvNavItem) other;
        return enabled == that.enabled
                && Objects.equals(title, that.title)
                && Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, action, enabled);
    }
}
