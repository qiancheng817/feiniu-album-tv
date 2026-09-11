package com.fnphoto.tv.browse

import com.google.gson.Gson
import com.fnphoto.tv.api.FnHttpApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DisplayBrowseMapperTest {
    @Test
    fun fromTag_defaultsMissingCountAndDropsBlankPoster() {
        val item = FnHttpApi.TagItem().apply {
            name = "旅行"
            itemCount = null
            posterUrl = " "
        }

        val category = requireNotNull(DisplayBrowseMapper.fromTag(item))

        assertEquals(DisplayTagIdentity.fromRaw("旅行"), category.id)
        assertEquals("旅行", category.title)
        assertEquals(0, category.count)
        assertNull(category.posterPath)
        assertEquals(DisplayCategorySource.Tag("旅行"), category.source)
    }

    @Test
    fun fromTag_skipsNullAndBlankNames() {
        assertNull(DisplayBrowseMapper.fromTag(FnHttpApi.TagItem()))
        assertNull(DisplayBrowseMapper.fromTag(FnHttpApi.TagItem().apply { name = " \t" }))
    }

    @Test
    fun fromTag_usesDistinctStableIdsForRawNamesWithTheSameTrimmedDisplay() {
        val firstRawName = "旅行"
        val secondRawName = "旅行 "

        val firstCategory = requireNotNull(DisplayBrowseMapper.fromTag(FnHttpApi.TagItem().apply {
            name = firstRawName
        }))
        val secondCategory = requireNotNull(DisplayBrowseMapper.fromTag(FnHttpApi.TagItem().apply {
            name = secondRawName
        }))

        assertNotEquals(firstCategory.id, secondCategory.id)
        assertEquals(DisplayTagIdentity.fromRaw(firstRawName), firstCategory.id)
        assertEquals(DisplayTagIdentity.fromRaw(secondRawName), secondCategory.id)
        assertEquals("旅行", secondCategory.title)
        assertEquals(DisplayCategorySource.Tag(secondRawName), secondCategory.source)
        val source = secondCategory.source as DisplayCategorySource.Tag
        assertEquals(
            """{"keyword":"","filters":[{"filterName":"photo_tag","filterValue":"旅行 "}],"antiFilters":[]}""",
            Gson().toJson(DisplaySearchRequestFactory.forTag(source.name)),
        )
    }

    @Test
    fun fromMediaCategory_mapsKnownServerCodesWithoutUsingServerLabels() {
        val expected = listOf(
            1 to ("照片" to "photo"),
            2 to ("视频" to "video"),
            3 to ("实况 / 动态照片" to "live_photo"),
            4 to ("动图" to "gif"),
            5 to ("RAW" to "raw"),
            6 to ("360° 照片视频" to "360"),
            7 to ("全景照片" to "panorama"),
            8 to ("屏幕截图" to "screenshot"),
        )

        expected.forEach { (code, values) ->
            val category = requireNotNull(DisplayBrowseMapper.fromMediaCategory(FnHttpApi.MediaCategoryItem().apply {
                this.category = code
                count = 0
                title = "server label must not win"
                fileType = "server-machine-value-must-not-win"
            }))

            assertEquals("media:$code", category.id)
            assertEquals(values.first, category.title)
            assertEquals(0, category.count)
            assertEquals(DisplayCategorySource.MediaType(code, values.second), category.source)
        }
    }

    @Test
    fun fromMediaCategory_keepsUnknownServerCategoryVisible() {
        val category = requireNotNull(DisplayBrowseMapper.fromMediaCategory(FnHttpApi.MediaCategoryItem().apply {
            this.category = 999
            count = 4
            title = "空间媒体"
            fileType = "spatial"
            id = 42
            uuid = "cover-uuid"
        }))

        assertEquals("media:999", category.id)
        assertEquals("空间媒体", category.title)
        assertEquals(4, category.count)
        assertEquals("/p/api/v1/stream/p/t/42/s/cover-uuid", category.posterPath)
        assertEquals(DisplayCategorySource.MediaType(999, "spatial"), category.source)
    }

    @Test
    fun fromMediaCategory_skipsUnknownCategoryWithMissingOrBlankTitle() {
        assertNull(DisplayBrowseMapper.fromMediaCategory(FnHttpApi.MediaCategoryItem().apply {
            category = 999
            fileType = "spatial"
        }))
        assertNull(DisplayBrowseMapper.fromMediaCategory(FnHttpApi.MediaCategoryItem().apply {
            category = 999
            title = " "
            fileType = "spatial"
        }))
    }

    @Test
    fun fromMediaCategory_skipsUnknownCategoryWithMissingOrBlankFileType() {
        assertNull(DisplayBrowseMapper.fromMediaCategory(FnHttpApi.MediaCategoryItem().apply {
            category = 999
            title = "空间媒体"
        }))
        assertNull(DisplayBrowseMapper.fromMediaCategory(FnHttpApi.MediaCategoryItem().apply {
            category = 999
            title = "空间媒体"
            fileType = "\t"
        }))
    }

    @Test
    fun fromMediaCategory_skipsMissingCategory() {
        assertNull(DisplayBrowseMapper.fromMediaCategory(FnHttpApi.MediaCategoryItem()))
    }
}
