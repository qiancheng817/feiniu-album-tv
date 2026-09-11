package com.fnphoto.tv.browse

import com.fnphoto.tv.api.FnHttpApi
import com.fnphoto.tv.api.FnRequestSigner
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

internal class DisplayBrowseRepository(
    private val api: FnHttpApi,
    private val accessToken: String,
    private val signer: FnRequestSigner = FnRequestSigner.Default,
    private val gson: Gson = Gson(),
) {
    suspend fun loadTags(): List<DisplayCategory> {
        val authx = signer.sign(TAGS_PATH, GET, TAGS_QUERY)
        val response = api.getTags(accessToken, authx, 0, -1).awaitCancellableResponse()
        if (!response.isSuccessful) {
            throw IOException("explore/tags HTTP ${response.code()}")
        }
        val body = response.body()
        if (body?.code != 0) {
            throw IOException("explore/tags code ${body?.code}")
        }
        return body.data?.list.orEmpty().mapNotNull(DisplayBrowseMapper::fromTag)
    }

    suspend fun loadMediaTypes(): List<DisplayCategory> {
        val authx = signer.sign(MEDIA_TYPES_PATH, GET, null)
        val response = api.getMediaCategories(accessToken, authx).awaitCancellableResponse()
        if (!response.isSuccessful) {
            throw IOException("media_category/list HTTP ${response.code()}")
        }
        val body = response.body()
        if (body?.code != 0) {
            throw IOException("media_category/list code ${body?.code}")
        }
        return body.data?.list.orEmpty().mapNotNull(DisplayBrowseMapper::fromMediaCategory)
    }

    suspend fun openPhotoPager(source: DisplayCategorySource): DisplayPhotoPager {
        val request = when (source) {
            is DisplayCategorySource.Tag -> DisplaySearchRequestFactory.forTag(source.name)
            is DisplayCategorySource.MediaType -> DisplaySearchRequestFactory.forMediaType(source.fileType)
        }
        val requestJson = gson.toJson(request)
        val authx = signer.sign(SEARCH_PATH, POST, requestJson)
        val response = api.searchFilteredPhotos(
            accessToken,
            authx,
            requestJson.toRequestBody(JSON_MEDIA_TYPE),
        ).awaitCancellableResponse()
        if (!response.isSuccessful) {
            throw IOException("search/results HTTP ${response.code()}")
        }
        val body = response.body()
        if (body?.code != 0) {
            throw IOException("search/results code ${body?.code}")
        }
        return DisplayPhotoPager(body.data?.list.orEmpty())
    }

    private companion object {
        const val GET = "GET"
        const val POST = "POST"
        const val TAGS_PATH = "/p/api/v1/explore/tags"
        const val TAGS_QUERY = "offset=0&limit=-1"
        const val MEDIA_TYPES_PATH = "/p/api/v1/media_category/list"
        const val SEARCH_PATH = "/p/api/v2/search/results"
        val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
    }
}

internal class DisplayPhotoPager(
    photos: List<FnHttpApi.GalleryPhoto>,
) {
    private val photos = photos.map(DisplayBrowsePhoto::fromApi)

    var offset = 0
        private set

    val hasMore: Boolean
        get() = offset < photos.size

    fun next(limit: Int): List<DisplayBrowsePhoto> {
        require(limit > 0)
        if (!hasMore) {
            return emptyList()
        }
        val end = (offset + limit).coerceAtMost(photos.size)
        return photos.subList(offset, end).also {
            offset = end
        }
    }
}
