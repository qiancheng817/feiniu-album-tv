package com.fnphoto.tv.browse

import com.fnphoto.tv.api.FnHttpApi

internal object DisplayBrowseMapper {
    fun fromTag(item: FnHttpApi.TagItem): DisplayCategory? {
        val rawName = item.name ?: return null
        if (rawName.isBlank()) {
            return null
        }
        val displayName = rawName.trim()

        return DisplayCategory(
            id = DisplayTagIdentity.fromRaw(rawName),
            title = displayName,
            count = item.itemCount ?: 0,
            posterPath = item.posterUrl?.takeIf { it.isNotBlank() },
            source = DisplayCategorySource.Tag(rawName),
        )
    }

    fun fromMediaCategory(item: FnHttpApi.MediaCategoryItem): DisplayCategory? {
        val categoryCode = item.category ?: return null
        val definition = knownMediaTypes[categoryCode]
        val title = definition?.title ?: item.title?.takeIf { it.isNotBlank() } ?: return null
        val fileType = definition?.fileType ?: item.fileType?.takeIf { it.isNotBlank() } ?: return null

        return DisplayCategory(
            id = "media:$categoryCode",
            title = title,
            count = item.count ?: 0,
            posterPath = item.posterPath(),
            source = DisplayCategorySource.MediaType(categoryCode, fileType),
        )
    }

    private fun FnHttpApi.MediaCategoryItem.posterPath(): String? {
        val itemId = id ?: return null
        val itemUuid = uuid?.takeIf { it.isNotBlank() } ?: return null
        if (itemId <= 0) {
            return null
        }
        return "/p/api/v1/stream/p/t/$itemId/s/$itemUuid"
    }

    private data class MediaDefinition(
        val title: String,
        val fileType: String,
    )

    private val knownMediaTypes = mapOf(
        1 to MediaDefinition("照片", "photo"),
        2 to MediaDefinition("视频", "video"),
        3 to MediaDefinition("实况 / 动态照片", "live_photo"),
        4 to MediaDefinition("动图", "gif"),
        5 to MediaDefinition("RAW", "raw"),
        6 to MediaDefinition("360° 照片视频", "360"),
        7 to MediaDefinition("全景照片", "panorama"),
        8 to MediaDefinition("屏幕截图", "screenshot"),
    )
}
