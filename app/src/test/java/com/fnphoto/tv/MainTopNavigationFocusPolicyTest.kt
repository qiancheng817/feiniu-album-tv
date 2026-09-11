package com.fnphoto.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainTopNavigationFocusPolicyTest {
    @Test
    fun selectedVisibleItemDoesNotNeedPreFocusScroll() {
        assertFalse(MainTopNavigationFocusPolicy.shouldScrollBeforeFocus(6, listOf(3, 4, 5, 6, 7)))
        assertEquals(
            MainTopNavigationFocusPolicy.FocusScrollBehavior.None,
            MainTopNavigationFocusPolicy.focusScrollBehavior(6, listOf(3, 4, 5, 6, 7)),
        )
    }

    @Test
    fun selectedOffscreenItemNeedsPreFocusScroll() {
        assertTrue(MainTopNavigationFocusPolicy.shouldScrollBeforeFocus(8, listOf(3, 4, 5, 6, 7)))
        assertEquals(
            MainTopNavigationFocusPolicy.FocusScrollBehavior.Animated,
            MainTopNavigationFocusPolicy.focusScrollBehavior(8, listOf(3, 4, 5, 6, 7)),
        )
    }

    @Test
    fun selectedItemScrollsWhenVisibilityIsUnknown() {
        assertTrue(MainTopNavigationFocusPolicy.shouldScrollBeforeFocus(1, emptyList()))
        assertEquals(
            MainTopNavigationFocusPolicy.FocusScrollBehavior.Animated,
            MainTopNavigationFocusPolicy.focusScrollBehavior(1, emptyList()),
        )
    }

    @Test
    fun focusReturnCanSkipPreScroll() {
        assertEquals(
            MainTopNavigationFocusPolicy.FocusScrollBehavior.None,
            MainTopNavigationFocusPolicy.focusScrollBehavior(
                selectedIndex = 8,
                visibleIndexes = listOf(3, 4, 5, 6, 7),
                preScrollAllowed = false,
            ),
        )
    }

    @Test
    fun invalidSelectionDoesNotScroll() {
        assertFalse(MainTopNavigationFocusPolicy.shouldScrollBeforeFocus(-1, listOf(0, 1, 2)))
        assertEquals(
            MainTopNavigationFocusPolicy.FocusScrollBehavior.None,
            MainTopNavigationFocusPolicy.focusScrollBehavior(-1, listOf(0, 1, 2)),
        )
    }

    @Test
    fun focusedTabUsesUnderlineAndFocusTextTone() {
        assertTrue(MainTopNavigationFocusPolicy.shouldShowUnderline(selected = false, focused = true))
        assertEquals(
            MainTopNavigationFocusPolicy.TopNavTextTone.Focus,
            MainTopNavigationFocusPolicy.topNavTextTone(selected = false, focused = true, enabled = true),
        )
    }

    @Test
    fun selectedTabKeepsUnderlineAndPrimaryTextTone() {
        assertTrue(MainTopNavigationFocusPolicy.shouldShowUnderline(selected = true, focused = false))
        assertEquals(
            MainTopNavigationFocusPolicy.TopNavTextTone.Primary,
            MainTopNavigationFocusPolicy.topNavTextTone(selected = true, focused = false, enabled = true),
        )
    }
}
