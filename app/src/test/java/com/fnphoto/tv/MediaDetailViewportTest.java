package com.fnphoto.tv;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MediaDetailViewportTest {
    @Test
    public void nextOkZoomScale_entersCoverZoomThenKeepsIncreasing() {
        float first = MediaDetailViewport.nextOkZoomScale(false, 1.0f, 2.4f);
        float second = MediaDetailViewport.nextOkZoomScale(true, first, 2.4f);
        float third = MediaDetailViewport.nextOkZoomScale(true, second, 2.4f);

        assertEquals(2.4f, first, 0.001f);
        assertTrue(second > first);
        assertTrue(third > second);
    }

    @Test
    public void zoomPanDelta_matchesViewportDirection() {
        assertEquals(120.0f, MediaDetailViewport.panDeltaX(KeyEvent.KEYCODE_DPAD_LEFT, 120.0f), 0.001f);
        assertEquals(-120.0f, MediaDetailViewport.panDeltaX(KeyEvent.KEYCODE_DPAD_RIGHT, 120.0f), 0.001f);
        assertEquals(120.0f, MediaDetailViewport.panDeltaY(KeyEvent.KEYCODE_DPAD_UP, 120.0f), 0.001f);
        assertEquals(-120.0f, MediaDetailViewport.panDeltaY(KeyEvent.KEYCODE_DPAD_DOWN, 120.0f), 0.001f);
    }
}
