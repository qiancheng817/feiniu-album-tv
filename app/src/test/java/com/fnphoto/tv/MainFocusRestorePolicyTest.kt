package com.fnphoto.tv

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainFocusRestorePolicyTest {
    @Test
    fun pendingContentRestoreRejectsIncidentalTopBarSnapshot() {
        assertFalse(
            MainFocusRestorePolicy.shouldRecordTopBarFocus(
                collectionRestorePending = true,
                photoRestorePending = false,
            ),
        )
        assertFalse(
            MainFocusRestorePolicy.shouldRecordTopBarFocus(
                collectionRestorePending = false,
                photoRestorePending = true,
            ),
        )
        assertTrue(
            MainFocusRestorePolicy.shouldRecordTopBarFocus(
                collectionRestorePending = false,
                photoRestorePending = false,
            ),
        )
    }

    @Test
    fun foundTargetIsConsumedOnlyAfterFocusConfirmation() {
        assertFalse(
            MainFocusRestorePolicy.shouldConsumeTarget(
                targetFound = true,
                focusConfirmed = false,
            ),
        )
        assertTrue(
            MainFocusRestorePolicy.shouldConsumeTarget(
                targetFound = true,
                focusConfirmed = true,
            ),
        )
        assertTrue(
            MainFocusRestorePolicy.shouldConsumeTarget(
                targetFound = false,
                focusConfirmed = false,
            ),
        )
    }

    @Test
    fun nestedPhotoStreamRequestsInitialContentFocusWhenNoRestoreIsPending() {
        assertTrue(
            MainFocusRestorePolicy.shouldRequestInitialPhotoFocus(
                restorePending = false,
                hasPhotos = true,
            ),
        )
        assertFalse(
            MainFocusRestorePolicy.shouldRequestInitialPhotoFocus(
                restorePending = true,
                hasPhotos = true,
            ),
        )
        assertFalse(
            MainFocusRestorePolicy.shouldRequestInitialPhotoFocus(
                restorePending = false,
                hasPhotos = false,
            ),
        )
    }
}
