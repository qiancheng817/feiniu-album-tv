package com.fnphoto.tv.browse

internal class LoadPublicationGate<S> {
    class Owner<S> internal constructor(
        internal val generation: Long,
        internal val source: S,
    )

    private var generation = 0L
    private var currentOwner: Owner<S>? = null

    @Synchronized
    fun begin(source: S): Owner<S> {
        return Owner(++generation, source).also {
            currentOwner = it
        }
    }

    @Synchronized
    fun invalidate() {
        generation++
        currentOwner = null
    }

    @Synchronized
    fun publish(owner: Owner<S>, publication: () -> Unit): Boolean {
        if (currentOwner != owner) {
            return false
        }
        publication()
        return true
    }
}

internal class DisplayBrowseLoadState {
    class Owner internal constructor(
        internal val generation: Long,
        val source: DisplayCategorySource,
    )

    data class Page(
        val photos: List<DisplayBrowsePhoto>,
        val offset: Int,
        val hasMore: Boolean,
    )

    private data class Session(
        val owner: Owner,
        var pager: DisplayPhotoPager? = null,
    )

    private var generation = 0L
    private var session: Session? = null

    @Synchronized
    fun begin(source: DisplayCategorySource): Owner {
        return Owner(++generation, source).also {
            session = Session(it)
        }
    }

    @Synchronized
    fun invalidate() {
        generation++
        session = null
    }

    suspend fun loadNext(
        owner: Owner,
        limit: Int,
        openPager: suspend () -> DisplayPhotoPager,
    ): Page? {
        val pager = currentPager(owner) ?: run {
            val loadedPager = openPager()
            if (!installPager(owner, loadedPager)) {
                return null
            }
            loadedPager
        }
        return nextPage(owner, pager, limit)
    }

    @Synchronized
    fun publish(owner: Owner, publication: () -> Unit): Boolean {
        if (session?.owner != owner) {
            return false
        }
        publication()
        return true
    }

    @Synchronized
    private fun currentPager(owner: Owner): DisplayPhotoPager? {
        return session?.takeIf { it.owner == owner }?.pager
    }

    @Synchronized
    private fun installPager(owner: Owner, pager: DisplayPhotoPager): Boolean {
        val current = session?.takeIf { it.owner == owner } ?: return false
        current.pager = pager
        return true
    }

    @Synchronized
    private fun nextPage(owner: Owner, pager: DisplayPhotoPager, limit: Int): Page? {
        val current = session?.takeIf { it.owner == owner && it.pager === pager } ?: return null
        val photos = current.pager?.next(limit).orEmpty()
        return Page(
            photos = photos,
            offset = current.pager?.offset ?: 0,
            hasMore = current.pager?.hasMore == true,
        )
    }
}
