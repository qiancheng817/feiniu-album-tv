package com.fnphoto.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoTileImagePolicyTest {
    @Test
    fun thumbnailDecodeSizeKeepsFocusScaleHeadroomWithoutOversizing() {
        assertEquals(448, PhotoTileImagePolicy.thumbnailDecodeSizePx)
    }
}
