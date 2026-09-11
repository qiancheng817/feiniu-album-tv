package com.fnphoto.tv.browse

import com.fnphoto.tv.api.FnHttpApi
import com.fnphoto.tv.api.FnRequestSigner
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class DisplayBrowseRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var signer: RecordingSigner
    private lateinit var repository: DisplayBrowseRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        signer = RecordingSigner()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FnHttpApi::class.java)
        repository = DisplayBrowseRepository(
            api = api,
            accessToken = ACCESS_TOKEN,
            signer = signer,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loadTags_signsCanonicalQueryAndDropsMalformedItems(): Unit = runBlocking {
        server.enqueue(jsonResponse(
            """
            {
              "code": 0,
              "msg": "ok",
              "data": {
                "count": 3,
                "hasNext": false,
                "list": [
                  {"name": " 旅行 ", "itemCount": 4, "posterUrl": "/synthetic/tag-cover.jpg"},
                  {"name": "   ", "itemCount": 2},
                  {"itemCount": 1}
                ]
              }
            }
            """.trimIndent(),
        ))

        val categories = repository.loadTags()

        assertEquals(
            SignedInput(
                path = "/p/api/v1/explore/tags",
                method = "GET",
                data = "offset=0&limit=-1",
            ),
            signer.lastInput,
        )
        assertEquals(1, categories.size)
        assertEquals(DisplayTagIdentity.fromRaw(" 旅行 "), categories.single().id)
        assertEquals("旅行", categories.single().title)
        assertEquals(DisplayCategorySource.Tag(" 旅行 "), categories.single().source)
        val request = server.takeRequest()
        assertEquals("/p/api/v1/explore/tags?offset=0&limit=-1", request.path)
        assertEquals(ACCESS_TOKEN, request.getHeader("accesstoken"))
        assertTrue(request.getHeader("authx").orEmpty().isNotBlank())
    }

    @Test
    fun loadMediaTypes_signsWithoutDataAndKeepsZeroCountCategorySelectable(): Unit = runBlocking {
        server.enqueue(jsonResponse(
            """
            {
              "code": 0,
              "msg": "ok",
              "data": {
                "list": [
                  {"category": 2, "count": 0, "id": 82, "uuid": "synthetic-video-cover"},
                  {"count": 3}
                ]
              }
            }
            """.trimIndent(),
        ))

        val categories = repository.loadMediaTypes()

        assertEquals(
            SignedInput(
                path = "/p/api/v1/media_category/list",
                method = "GET",
                data = null,
            ),
            signer.lastInput,
        )
        assertEquals(1, categories.size)
        assertEquals(0, categories.single().count)
        assertEquals(DisplayCategorySource.MediaType(2, "video"), categories.single().source)
        val request = server.takeRequest()
        assertEquals("/p/api/v1/media_category/list", request.path)
        assertEquals(ACCESS_TOKEN, request.getHeader("accesstoken"))
        assertTrue(request.getHeader("authx").orEmpty().isNotBlank())
    }

    @Test
    fun openPhotoPager_postsOnceAndReturnsBoundedLocalPages(): Unit = runBlocking {
        server.enqueue(jsonResponse(searchResponseJson(photoCount = 125)))

        val pager = repository.openPhotoPager(DisplayCategorySource.MediaType(2, "video"))

        assertEquals(
            SignedInput(
                path = "/p/api/v2/search/results",
                method = "POST",
                data = """{"keyword":"","filters":[{"filterName":"file_type","filterValue":"video"}],"antiFilters":[]}""",
            ),
            signer.lastInput,
        )
        assertEquals((1..96).map(Int::toString), pager.next(96).map { it.id })
        assertEquals((97..125).map(Int::toString), pager.next(96).map { it.id })
        assertEquals(emptyList(), pager.next(96))
        assertEquals(1, server.requestCount)

        val request = server.takeRequest()
        assertEquals("/p/api/v2/search/results", request.path)
        assertEquals(
            """{"keyword":"","filters":[{"filterName":"file_type","filterValue":"video"}],"antiFilters":[]}""",
            request.body.readUtf8(),
        )
        assertEquals(ACCESS_TOKEN, request.getHeader("accesstoken"))
        assertTrue(request.getHeader("authx").orEmpty().isNotBlank())
    }

    @Test
    fun openPhotoPager_usesRawTagNameInExactMutuallyExclusiveBody(): Unit = runBlocking {
        server.enqueue(jsonResponse(searchResponseJson(photoCount = 0)))

        repository.openPhotoPager(DisplayCategorySource.Tag("旅行 "))

        assertEquals(
            SignedInput(
                path = "/p/api/v2/search/results",
                method = "POST",
                data = """{"keyword":"","filters":[{"filterName":"photo_tag","filterValue":"旅行 "}],"antiFilters":[]}""",
            ),
            signer.lastInput,
        )
        assertEquals(
            """{"keyword":"","filters":[{"filterName":"photo_tag","filterValue":"旅行 "}],"antiFilters":[]}""",
            server.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun openPhotoPager_rejectsHttpAndApplicationErrors(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertFailsWith<IOException> {
            repository.openPhotoPager(DisplayCategorySource.MediaType(2, "video"))
        }

        server.enqueue(jsonResponse("""{"code": 23, "msg": "synthetic failure", "data": {"list": []}}"""))
        assertFailsWith<IOException> {
            repository.openPhotoPager(DisplayCategorySource.MediaType(2, "video"))
        }
    }

    @Test
    fun photoPagerDoesNotRetainHeavyApiRecords() {
        val apiPhoto = FnHttpApi.GalleryPhoto().apply {
            id = 7
            fileName = "synthetic-photo.jpg"
            category = "photo"
            photoDateTime = "2026-07-26 08:30:00"
            width = 4032
            height = 3024
            additional = FnHttpApi.GalleryPhotoAdditional().apply {
                thumbnail = FnHttpApi.GalleryThumbnail().apply {
                    mUrl = "/synthetic/medium.jpg"
                    originalUrl = "/synthetic/original.jpg"
                }
            }
        }

        val compact = DisplayPhotoPager(listOf(apiPhoto)).next(1).single()

        assertNotSame<Any>(apiPhoto, compact)
        assertEquals("7", compact.id)
        assertEquals("synthetic-photo.jpg", compact.fileName)
        assertEquals("/synthetic/medium.jpg", compact.thumbnailPath)
        assertEquals("/synthetic/original.jpg", compact.mediaPath)
    }

    private fun jsonResponse(body: String): MockResponse {
        return MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }

    private fun searchResponseJson(photoCount: Int): String {
        val photos = (1..photoCount).joinToString(",") { id ->
            """
            {
              "id": $id,
              "ownerId": 9,
              "dateTime": "2026-07-25 12:00:00",
              "photoDateTime": "2026-07-25 12:00:00",
              "fileType": "mp4",
              "category": "video",
              "fileName": "synthetic-$id.mp4",
              "fileSize": 1000,
              "isCollect": 0,
              "height": 1080,
              "width": 1920,
              "isLive": 0,
              "rotation": 0,
              "isCanPreview": 1,
              "photoUUID": "synthetic-uuid-$id"
            }
            """.trimIndent()
        }
        return """
            {
              "code": 0,
              "msg": "ok",
              "data": {
                "list": [$photos],
                "count": $photoCount,
                "hasNext": false,
                "total": $photoCount
              }
            }
        """.trimIndent()
    }

    private class RecordingSigner : FnRequestSigner {
        var lastInput: SignedInput? = null
            private set

        override fun sign(path: String, method: String, data: String?): String {
            lastInput = SignedInput(path, method, data)
            return "synthetic-authx"
        }
    }

    private companion object {
        const val ACCESS_TOKEN = "synthetic-access-token"
    }
}

private data class SignedInput(
    val path: String,
    val method: String,
    val data: String?,
)
