package com.fnphoto.tv.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckPolicyTest {
    @Test
    fun shouldCheck_returnsTrueWhenForced() {
        assertTrue(
            UpdateCheckPolicy.shouldCheck(
                nowMillis = 10_000L,
                lastCheckMillis = 9_999L,
                force = true,
            ),
        )
    }

    @Test
    fun shouldCheck_returnsTrueWhenNeverChecked() {
        assertTrue(
            UpdateCheckPolicy.shouldCheck(
                nowMillis = 10_000L,
                lastCheckMillis = 0L,
                force = false,
            ),
        )
    }

    @Test
    fun shouldCheck_throttlesChecksWithinOneDay() {
        assertFalse(
            UpdateCheckPolicy.shouldCheck(
                nowMillis = 86_400_000L,
                lastCheckMillis = 1L,
                force = false,
            ),
        )
    }

    @Test
    fun shouldCheck_allowsChecksAfterOneDay() {
        assertTrue(
            UpdateCheckPolicy.shouldCheck(
                nowMillis = 86_400_001L,
                lastCheckMillis = 1L,
                force = false,
            ),
        )
    }
}
