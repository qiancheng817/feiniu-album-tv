package com.fnphoto.tv.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionComparatorTest {
    @Test
    fun isRemoteNewer_acceptsLeadingVAndPatchBumps() {
        assertTrue(VersionComparator.isRemoteNewer(remote = "v1.0.1", current = "1.0"))
    }

    @Test
    fun isRemoteNewer_rejectsSameVersionWithDifferentPrefix() {
        assertFalse(VersionComparator.isRemoteNewer(remote = "v1.2.3", current = "1.2.3"))
    }

    @Test
    fun isRemoteNewer_comparesEachNumericSegment() {
        assertTrue(VersionComparator.isRemoteNewer(remote = "1.10.0", current = "1.9.9"))
        assertFalse(VersionComparator.isRemoteNewer(remote = "1.2.0", current = "1.2.1"))
    }

    @Test
    fun isRemoteNewer_ignoresNonNumericSuffixes() {
        assertTrue(VersionComparator.isRemoteNewer(remote = "v2.0.0-release", current = "1.9.9"))
        assertFalse(VersionComparator.isRemoteNewer(remote = "v2.0.0-beta", current = "2.0.0"))
    }
}
