package com.fnphoto.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainPageBackStackTest {
    @Test
    fun pushCurrent_ignoresNavigationToSameRoute() {
        val stack = MainPageBackStack<String, String>()

        val changed = stack.pushCurrent(currentRoute = "gallery", focus = "gallery-photo-2", nextRoute = "gallery")

        assertFalse(changed)
        assertFalse(stack.canGoBack)
        assertNull(stack.popPrevious())
    }

    @Test
    fun popPrevious_returnsPagesInLastInFirstOutOrderWithSavedFocus() {
        val stack = MainPageBackStack<String, String>()

        assertTrue(stack.pushCurrent(currentRoute = "gallery", focus = "gallery-photo-2", nextRoute = "albums"))
        assertTrue(stack.pushCurrent(currentRoute = "albums", focus = "album-card-7", nextRoute = "album:42"))

        assertTrue(stack.canGoBack)
        assertEquals(
            MainPageBackStack.Entry(route = "albums", focus = "album-card-7"),
            stack.popPrevious(),
        )
        assertEquals(
            MainPageBackStack.Entry(route = "gallery", focus = "gallery-photo-2"),
            stack.popPrevious(),
        )
        assertFalse(stack.canGoBack)
        assertNull(stack.popPrevious())
    }

    @Test
    fun resetTo_clearsPreviousPages() {
        val stack = MainPageBackStack<String, String>()

        stack.pushCurrent(currentRoute = "gallery", focus = "gallery-photo-2", nextRoute = "albums")
        stack.resetTo("people")

        assertFalse(stack.canGoBack)
        assertNull(stack.popPrevious())
    }
}
