package com.fnphoto.tv.browse

import com.fnphoto.tv.api.FnHttpApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DisplayBrowseLifecycleTest {
    @Test
    fun tokenRefreshDuringSuccessfulRetry_preservesOwnedPagerAndContentPublication(): Unit = runBlocking {
        val lifecycle = DisplayBrowseLifecycle()
        val owner = lifecycle.begin(
            loadIdentity = "direct:tag:travel",
            source = DisplayCategorySource.Tag("旅行"),
        )
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRetry = CompletableDeferred<Unit>()
        val retriedPage = async {
            lifecycle.loadNext(owner, limit = 1) {
                requestStarted.complete(Unit)
                releaseRetry.await()
                pagerWithIds(7, 8)
            }
        }
        requestStarted.await()

        lifecycle.onSessionTokenChanged()
        releaseRetry.complete(Unit)

        val page = requireNotNull(retriedPage.await())
        assertEquals(listOf("7"), page.photos.map { it.id })
        assertEquals(1, page.offset)
        assertTrue(page.hasMore)
        var uiState = "loading"
        assertTrue(lifecycle.publish(owner) { uiState = "content:7" })
        assertEquals("content:7", uiState)
    }

    @Test
    fun tokenRefreshDuringSuccessfulEmptyRetry_publishesOwnedEmptyState(): Unit = runBlocking {
        val lifecycle = DisplayBrowseLifecycle()
        val owner = lifecycle.begin(
            loadIdentity = "direct:media:video",
            source = DisplayCategorySource.MediaType(2, "video"),
        )

        lifecycle.onSessionTokenChanged()
        val page = requireNotNull(lifecycle.loadNext(owner, limit = 96) {
            DisplayPhotoPager(emptyList())
        })

        assertEquals(0, page.offset)
        assertFalse(page.hasMore)
        var uiState = "loading"
        assertTrue(lifecycle.publish(owner) { uiState = "empty" })
        assertEquals("empty", uiState)
    }

    @Test
    fun tokenRefreshThenCategoryChange_rejectsObsoleteFailureAndKeepsNewContent(): Unit = runBlocking {
        supervisorScope {
            val lifecycle = DisplayBrowseLifecycle()
            val obsoleteOwner = lifecycle.begin(
                loadIdentity = "direct:tag:travel",
                source = DisplayCategorySource.Tag("旅行"),
            )
            val requestStarted = CompletableDeferred<Unit>()
            val releaseFailure = CompletableDeferred<Unit>()
            val obsoleteRequest = async {
                lifecycle.loadNext(obsoleteOwner, limit = 1) {
                    requestStarted.complete(Unit)
                    releaseFailure.await()
                    throw IOException("synthetic obsolete failure")
                }
            }
            requestStarted.await()
            lifecycle.onSessionTokenChanged()

            val activeOwner = lifecycle.begin(
                loadIdentity = "direct:tag:travel-space",
                source = DisplayCategorySource.Tag("旅行 "),
            )
            val activePage = requireNotNull(lifecycle.loadNext(activeOwner, limit = 1) {
                pagerWithIds(9)
            })
            var uiState = "loading"
            assertTrue(lifecycle.publish(activeOwner) {
                uiState = "content:${activePage.photos.single().id}"
            })
            releaseFailure.complete(Unit)

            assertIs<IOException>(runCatching { obsoleteRequest.await() }.exceptionOrNull())
            assertFalse(lifecycle.publish(obsoleteOwner) { uiState = "error" })
            assertEquals("content:9", uiState)
        }
    }

    private fun pagerWithIds(vararg ids: Int): DisplayPhotoPager {
        return DisplayPhotoPager(ids.map { id ->
            FnHttpApi.GalleryPhoto().apply { this.id = id }
        })
    }
}
