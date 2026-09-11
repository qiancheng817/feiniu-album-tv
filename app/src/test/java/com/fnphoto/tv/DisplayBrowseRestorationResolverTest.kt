package com.fnphoto.tv

import com.fnphoto.tv.api.FnHttpApi
import com.fnphoto.tv.browse.DisplayBrowseMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DisplayBrowseRestorationResolverTest {
    @Test
    fun coldTagRestoration_targetsTheCardForTheExactRawTag() {
        val compact = requireNotNull(DisplayBrowseMapper.fromTag(tag("旅行")))
        val spaced = requireNotNull(DisplayBrowseMapper.fromTag(tag("旅行 ")))
        assertNotEquals(compact.id, spaced.id)

        val restoration = requireNotNull(
            DisplayBrowseRestorationResolver.forTag(
                rawName = "旅行 ",
                displayTitle = "旅行",
            ),
        )

        assertEquals(MainPageRoute.Tags, restoration.parentRoute)
        assertEquals(MainCollectionFocusTarget("tag", spaced.id), restoration.parentFocusTarget)
        assertEquals(
            MainPageRoute.DirectPhotos(StreamSource.tag("旅行 ", "旅行")),
            restoration.route,
        )
    }

    @Test
    fun coldMediaTypeRestoration_rebuildsParentCardAndExactFilterSource() {
        val restoration = requireNotNull(
            DisplayBrowseRestorationResolver.forMediaType(
                categoryCode = 2,
                fileType = "video",
                displayTitle = "视频",
            ),
        )

        assertEquals(MainPageRoute.MediaTypes, restoration.parentRoute)
        assertEquals(MainCollectionFocusTarget("media_type", "media:2"), restoration.parentFocusTarget)
        assertEquals(
            MainPageRoute.DirectPhotos(StreamSource.mediaType(2, "video", "视频")),
            restoration.route,
        )
    }

    private fun tag(rawName: String): FnHttpApi.TagItem {
        return FnHttpApi.TagItem().apply { name = rawName }
    }
}
