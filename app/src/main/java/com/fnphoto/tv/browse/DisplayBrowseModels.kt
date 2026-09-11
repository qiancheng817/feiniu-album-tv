package com.fnphoto.tv.browse

import com.fnphoto.tv.api.FnHttpApi
import com.google.gson.annotations.SerializedName

internal sealed interface DisplayCategorySource {
    data class Tag(val name: String) : DisplayCategorySource
    data class MediaType(val categoryCode: Int, val fileType: String) : DisplayCategorySource
}

internal data class DisplayCategory(
    val id: String,
    val title: String,
    val count: Int,
    val posterPath: String?,
    val source: DisplayCategorySource,
)

internal data class DisplayBrowsePhoto(
    val id: String,
    val fileName: String,
    val category: String,
    val thumbnailPath: String?,
    val mediaPath: String?,
    val takenAt: String?,
    val width: Int,
    val height: Int,
) {
    companion object {
        fun fromApi(photo: FnHttpApi.GalleryPhoto): DisplayBrowsePhoto {
            val thumbnail = photo.additional?.thumbnail
            return DisplayBrowsePhoto(
                id = photo.id.toString(),
                fileName = photo.fileName.orEmpty(),
                category = photo.category ?: "photo",
                thumbnailPath = thumbnail?.mUrl
                    ?: thumbnail?.sUrl
                    ?: thumbnail?.xsUrl
                    ?: thumbnail?.xxsUrl,
                mediaPath = thumbnail?.originalUrl
                    ?: thumbnail?.mUrl
                    ?: thumbnail?.sUrl
                    ?: thumbnail?.videoUrl,
                takenAt = photo.photoDateTime?.takeIf { it.isNotBlank() } ?: photo.dateTime,
                width = photo.width,
                height = photo.height,
            )
        }
    }
}

internal data class DisplaySearchFilter(
    @SerializedName("filterName") val filterName: String,
    @SerializedName("filterValue") val filterValue: String,
)

internal data class DisplaySearchRequest(
    val keyword: String = "",
    val filters: List<DisplaySearchFilter>,
    val antiFilters: List<DisplaySearchFilter> = emptyList(),
)
