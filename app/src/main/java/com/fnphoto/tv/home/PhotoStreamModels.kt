package com.fnphoto.tv.home

import com.fnphoto.tv.api.FnHttpApi
import com.fnphoto.tv.browse.DisplayBrowsePhoto
import com.fnphoto.tv.ui.TvUiText

data class PhotoStreamPhoto(
    val id: String,
    val title: String,
    val type: String,
    val thumbnailUrl: String?,
    val mediaUrl: String?,
    val takenAt: String?,
    val width: Int,
    val height: Int,
) {
    val overlayTitle: String
        get() = TvUiText.cardOverlayTitle(type, title)
}

data class PhotoStreamGroup(
    val title: String,
    val photos: List<PhotoStreamPhoto>,
)

object PhotoStreamRenderKeys {
    fun keyFor(groupIndex: Int, photoIndex: Int, photoId: String): String {
        return "photo-$groupIndex-$photoIndex-$photoId"
    }

    fun keysFor(groups: List<PhotoStreamGroup>): List<String> {
        val keys = mutableListOf<String>()
        groups.forEachIndexed { groupIndex, group ->
            group.photos.forEachIndexed { photoIndex, photo ->
                keys.add(keyFor(groupIndex, photoIndex, photo.id))
            }
        }
        return keys
    }
}

object PhotoStreamGrouper {
    fun groupByDay(photos: List<PhotoStreamPhoto>): List<PhotoStreamGroup> {
        val groups = linkedMapOf<String, MutableList<PhotoStreamPhoto>>()
        for (photo in photos) {
            val title = dayTitle(photo.takenAt)
            groups.getOrPut(title) { mutableListOf() }.add(photo)
        }
        return groups.map { (title, items) -> PhotoStreamGroup(title, items) }
    }

    fun appendByDay(
        existingGroups: List<PhotoStreamGroup>,
        appendedPhotos: List<PhotoStreamPhoto>,
    ): List<PhotoStreamGroup> {
        if (appendedPhotos.isEmpty()) {
            return existingGroups
        }
        if (existingGroups.isEmpty()) {
            return groupByDay(appendedPhotos)
        }

        val result = existingGroups.toMutableList()
        for (newGroup in groupByDay(appendedPhotos)) {
            val existingIndex = result.indexOfFirst { it.title == newGroup.title }
            if (existingIndex >= 0) {
                val existingGroup = result[existingIndex]
                result[existingIndex] = existingGroup.copy(
                    photos = existingGroup.photos + newGroup.photos,
                )
            } else {
                result.add(newGroup)
            }
        }
        return result
    }

    fun dayTitle(rawDate: String?): String {
        if (rawDate.isNullOrBlank() || rawDate.length < 10) {
            return TvUiText.homeRecentPhotosTitle()
        }

        val normalized = rawDate.replace(':', '-')
        return try {
            val year = normalized.substring(0, 4).toInt()
            val month = normalized.substring(5, 7).toInt()
            val day = normalized.substring(8, 10).toInt()
            TvUiText.photoDateTitle(year, month, day)
        } catch (_: RuntimeException) {
            TvUiText.homeRecentPhotosTitle()
        }
    }
}

object PhotoStreamMapper {
    fun fromGalleryPhoto(photo: FnHttpApi.GalleryPhoto, baseUrl: String): PhotoStreamPhoto {
        val thumbnail = photo.additional?.thumbnail
        val thumbnailUrl = absoluteUrl(
            baseUrl,
            thumbnail?.mUrl ?: thumbnail?.sUrl ?: thumbnail?.xsUrl ?: thumbnail?.xxsUrl,
        )
        val mediaUrl = absoluteUrl(
            baseUrl,
            thumbnail?.originalUrl ?: thumbnail?.mUrl ?: thumbnail?.sUrl ?: thumbnail?.videoUrl,
        )

        return PhotoStreamPhoto(
            id = photo.id.toString(),
            title = photo.fileName.orEmpty(),
            type = photo.category ?: "photo",
            thumbnailUrl = thumbnailUrl,
            mediaUrl = mediaUrl,
            takenAt = photo.photoDateTime?.takeIf { it.isNotBlank() } ?: photo.dateTime,
            width = photo.width,
            height = photo.height,
        )
    }

    fun fromFolderMediaItem(item: FnHttpApi.FolderMediaItem, baseUrl: String): PhotoStreamPhoto {
        val thumbnailUrl = absoluteUrl(
            baseUrl,
            "/p/api/v1/stream/p/t/${item.id}/s/${item.photoUUID}",
        )
        val mediaPath = if (item.category == "video") {
            "/p/api/v1/stream/v/${item.id}"
        } else {
            "/p/api/v1/stream/p/t/${item.id}/o/${item.photoUUID}"
        }

        return PhotoStreamPhoto(
            id = item.id.toString(),
            title = item.fileName.orEmpty(),
            type = item.category ?: "photo",
            thumbnailUrl = thumbnailUrl,
            mediaUrl = absoluteUrl(baseUrl, mediaPath),
            takenAt = item.photoDateTime?.takeIf { it.isNotBlank() } ?: item.dateTime,
            width = item.width,
            height = item.height,
        )
    }

    internal fun fromDisplayBrowsePhoto(photo: DisplayBrowsePhoto, baseUrl: String): PhotoStreamPhoto {
        return PhotoStreamPhoto(
            id = photo.id,
            title = photo.fileName,
            type = photo.category,
            thumbnailUrl = absoluteUrl(baseUrl, photo.thumbnailPath),
            mediaUrl = absoluteUrl(baseUrl, photo.mediaPath),
            takenAt = photo.takenAt,
            width = photo.width,
            height = photo.height,
        )
    }

    private fun absoluteUrl(baseUrl: String, path: String?): String? {
        if (path.isNullOrBlank()) {
            return null
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}
