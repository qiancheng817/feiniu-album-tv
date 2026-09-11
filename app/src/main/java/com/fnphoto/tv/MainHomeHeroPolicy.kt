package com.fnphoto.tv

internal object MainHomeHeroPolicy {
    fun shouldUpdateHeroOnPhotoFocus(
        currentHeroUrl: String?,
        focusedPhotoUrl: String?,
    ): Boolean {
        return false
    }
}
