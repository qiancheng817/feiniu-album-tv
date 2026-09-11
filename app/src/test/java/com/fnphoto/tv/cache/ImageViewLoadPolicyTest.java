package com.fnphoto.tv.cache;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ImageViewLoadPolicyTest {
    @Test
    public void imageViewLoadUsesProvidedTokenWhenPresent() {
        assertEquals(
                "provided-token",
                ImageViewLoadPolicy.resolveToken("provided-token", "stored-token")
        );
    }

    @Test
    public void imageViewLoadFallsBackToStoredTokenWhenProvidedTokenMissing() {
        assertEquals(
                "stored-token",
                ImageViewLoadPolicy.resolveToken("", "stored-token")
        );
    }

    @Test
    public void imageViewLoadDoesNotProbeLegacyCacheOnMainPath() {
        assertFalse(ImageViewLoadPolicy.shouldProbeLegacyCacheBeforeGlide());
    }
}
