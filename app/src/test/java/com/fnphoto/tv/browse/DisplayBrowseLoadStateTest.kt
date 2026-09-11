package com.fnphoto.tv.browse

import com.fnphoto.tv.api.FnHttpApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayBrowseLoadStateTest {
    @Test
    fun switchingRawTagWhileOldPagerIsDelayed_keepsNewPagerOffsetAndPublication() = runBlocking {
        val state = DisplayBrowseLoadState()
        val oldOwner = state.begin(DisplayCategorySource.Tag("旅行"))
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val oldPage = async {
            state.loadNext(oldOwner, limit = 1) {
                oldStarted.complete(Unit)
                releaseOld.await()
                pagerWithIds(1)
            }
        }
        oldStarted.await()

        val newOwner = state.begin(DisplayCategorySource.Tag("旅行 "))
        val firstNewPage = state.loadNext(newOwner, limit = 1) {
            pagerWithIds(2, 3)
        }
        releaseOld.complete(Unit)

        assertNull(oldPage.await())
        assertEquals(listOf("2"), firstNewPage?.photos?.map { it.id })
        assertEquals(1, firstNewPage?.offset)
        assertEquals(true, firstNewPage?.hasMore)

        val secondNewPage = state.loadNext(newOwner, limit = 1) {
            error("new pager must be retained")
        }
        assertEquals(listOf("3"), secondNewPage?.photos?.map { it.id })
        assertEquals(2, secondNewPage?.offset)
        assertEquals(false, secondNewPage?.hasMore)

        var published = "none"
        assertFalse(state.publish(oldOwner) { published = "old" })
        assertTrue(state.publish(newOwner) { published = "new" })
        assertEquals("new", published)
    }

    @Test
    fun switchingCollectionRouteWhileOldItemsAreDelayed_preventsOldUiPublication() = runBlocking {
        val gate = LoadPublicationGate<String>()
        val oldOwner = gate.begin("tags")
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        var published = "loading"
        val oldPublication = async {
            oldStarted.complete(Unit)
            releaseOld.await()
            gate.publish(oldOwner) { published = "old-tags" }
        }
        oldStarted.await()

        val newOwner = gate.begin("media_types")
        assertTrue(gate.publish(newOwner) { published = "media-types" })
        releaseOld.complete(Unit)

        assertFalse(oldPublication.await())
        assertEquals("media-types", published)
    }

    private fun pagerWithIds(vararg ids: Int): DisplayPhotoPager {
        return DisplayPhotoPager(ids.map { id ->
            FnHttpApi.GalleryPhoto().apply { this.id = id }
        })
    }
}
