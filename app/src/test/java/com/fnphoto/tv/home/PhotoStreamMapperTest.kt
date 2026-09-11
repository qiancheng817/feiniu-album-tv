package com.fnphoto.tv.home

import com.fnphoto.tv.api.FnHttpApi
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoStreamMapperTest {
    @Test
    fun fromGalleryPhoto_prefersMediumThumbnailAndOriginalMediaUrl() {
        val thumbnail = FnHttpApi.GalleryThumbnail().apply {
            mUrl = "/p/thumb/m.jpg"
            sUrl = "/p/thumb/s.jpg"
            originalUrl = "/p/original.jpg"
        }
        val photo = FnHttpApi.GalleryPhoto().apply {
            id = 42
            fileName = "IMG_0042.jpg"
            category = "photo"
            dateTime = "2026:06:20 08:00:00"
            photoDateTime = ""
            width = 4032
            height = 3024
            additional = FnHttpApi.GalleryPhotoAdditional().apply {
                this.thumbnail = thumbnail
            }
        }

        val item = PhotoStreamMapper.fromGalleryPhoto(photo, "http://nas.test")

        assertEquals("42", item.id)
        assertEquals("IMG_0042.jpg", item.title)
        assertEquals("photo", item.type)
        assertEquals("http://nas.test/p/thumb/m.jpg", item.thumbnailUrl)
        assertEquals("http://nas.test/p/original.jpg", item.mediaUrl)
        assertEquals("2026:06:20 08:00:00", item.takenAt)
        assertEquals(4032, item.width)
        assertEquals(3024, item.height)
    }
}
