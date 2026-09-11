package com.fnphoto.tv

internal object MainFocusRestorePolicy {
    fun shouldRecordTopBarFocus(
        collectionRestorePending: Boolean,
        photoRestorePending: Boolean,
    ): Boolean {
        return !collectionRestorePending && !photoRestorePending
    }

    fun shouldConsumeTarget(
        targetFound: Boolean,
        focusConfirmed: Boolean,
    ): Boolean {
        return !targetFound || focusConfirmed
    }

    fun shouldRequestInitialPhotoFocus(
        restorePending: Boolean,
        hasPhotos: Boolean,
    ): Boolean {
        return !restorePending && hasPhotos
    }
}
