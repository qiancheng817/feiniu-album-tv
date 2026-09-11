package com.fnphoto.tv.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoStreamGrouperTest {
    @Test
    fun groupByDay_preservesTimelineOrderAndOmitsPhotoTitles() {
        val photos = listOf(
            PhotoStreamPhoto(
                id = "1",
                title = "IMG_0001.jpg",
                type = "photo",
                thumbnailUrl = "/thumb/1.jpg",
                mediaUrl = "/media/1.jpg",
                takenAt = "2026:06:24 20:10:00",
                width = 3024,
                height = 4032,
            ),
            PhotoStreamPhoto(
                id = "2",
                title = "IMG_0002.jpg",
                type = "photo",
                thumbnailUrl = "/thumb/2.jpg",
                mediaUrl = "/media/2.jpg",
                takenAt = "2026-06-24 12:00:00",
                width = 4032,
                height = 3024,
            ),
            PhotoStreamPhoto(
                id = "3",
                title = "VID_0003.mp4",
                type = "video",
                thumbnailUrl = "/thumb/3.jpg",
                mediaUrl = "/media/3.mp4",
                takenAt = "2026:06:23 10:00:00",
                width = 1920,
                height = 1080,
            ),
        )

        val groups = PhotoStreamGrouper.groupByDay(photos)

        assertEquals(listOf("6月24日 星期三", "6月23日 星期二"), groups.map { it.title })
        assertEquals(listOf("1", "2"), groups.first().photos.map { it.id })
        assertTrue(groups.flatMap { it.photos }.all { it.overlayTitle.isEmpty() })
    }

    @Test
    fun renderKeys_areUniqueWhenServerReturnsDuplicatePhotoIds() {
        val groups = listOf(
            PhotoStreamGroup(
                title = "7月4日 星期六",
                photos = listOf(photo("1"), photo("2")),
            ),
            PhotoStreamGroup(
                title = "7月3日 星期五",
                photos = listOf(photo("1"), photo("2")),
            ),
        )

        val keys = PhotoStreamRenderKeys.keysFor(groups)

        assertEquals(4, keys.size)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun appendByDay_reusesExistingGroupsAndMergesOnlyAppendedPhotos() {
        val unchangedGroup = PhotoStreamGroup(
            title = "7月5日 星期日",
            photos = listOf(photo("0", takenAt = "2026-07-05 18:00:00")),
        )
        val mergeTargetGroup = PhotoStreamGroup(
            title = "7月4日 星期六",
            photos = listOf(photo("1", takenAt = "2026-07-04 18:00:00")),
        )
        val existing = listOf(unchangedGroup, mergeTargetGroup)

        val appended = PhotoStreamGrouper.appendByDay(
            existing,
            listOf(
                photo("2", takenAt = "2026-07-04 18:00:00"),
                photo("3", takenAt = "2026-07-03 18:00:00"),
            ),
        )

        assertEquals(listOf("7月5日 星期日", "7月4日 星期六", "7月3日 星期五"), appended.map { it.title })
        assertTrue(unchangedGroup === appended[0])
        assertEquals(listOf("1", "2"), appended[1].photos.map { it.id })
        assertEquals(listOf("3"), appended[2].photos.map { it.id })
    }

    private fun photo(id: String, takenAt: String = "2026-07-04 12:00:00"): PhotoStreamPhoto {
        return PhotoStreamPhoto(
            id = id,
            title = "IMG_$id.jpg",
            type = "photo",
            thumbnailUrl = "/thumb/$id.jpg",
            mediaUrl = "/media/$id.jpg",
            takenAt = takenAt,
            width = 4032,
            height = 3024,
        )
    }
}
