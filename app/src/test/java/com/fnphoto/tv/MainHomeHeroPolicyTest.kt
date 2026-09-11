package com.fnphoto.tv

import kotlin.test.Test
import kotlin.test.assertFalse

class MainHomeHeroPolicyTest {
    @Test
    fun photoFocusDoesNotReplaceExistingHeroImage() {
        assertFalse(
            MainHomeHeroPolicy.shouldUpdateHeroOnPhotoFocus(
                currentHeroUrl = "https://example.test/first.jpg",
                focusedPhotoUrl = "https://example.test/next.jpg",
            ),
        )
    }

    @Test
    fun photoFocusDoesNotStartHeroImageLoadWhenHeroIsEmpty() {
        assertFalse(
            MainHomeHeroPolicy.shouldUpdateHeroOnPhotoFocus(
                currentHeroUrl = null,
                focusedPhotoUrl = "https://example.test/next.jpg",
            ),
        )
    }
}
