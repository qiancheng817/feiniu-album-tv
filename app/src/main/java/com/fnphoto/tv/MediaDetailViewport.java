package com.fnphoto.tv;

import android.view.KeyEvent;

public final class MediaDetailViewport {
    private static final float OK_ZOOM_STEP = 1.35f;
    private static final float MAX_ZOOM_SCALE = 64.0f;

    private MediaDetailViewport() {
    }

    public static float nextOkZoomScale(boolean alreadyZoomed, float currentScale, float coverScale) {
        if (!alreadyZoomed) {
            return Math.max(1.0f, coverScale);
        }
        return Math.min(MAX_ZOOM_SCALE, Math.max(1.0f, currentScale) * OK_ZOOM_STEP);
    }

    public static float panDeltaX(int keyCode, float step) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return step;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return -step;
        }
        return 0.0f;
    }

    public static float panDeltaY(int keyCode, float step) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return step;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return -step;
        }
        return 0.0f;
    }
}
