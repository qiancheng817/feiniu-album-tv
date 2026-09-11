package com.fnphoto.tv.browse

internal class DisplayBrowseLifecycle(
    private val publicationGate: LoadPublicationGate<String> = LoadPublicationGate(),
    private val loadState: DisplayBrowseLoadState = DisplayBrowseLoadState(),
) {
    class Owner internal constructor(
        internal val publicationOwner: LoadPublicationGate.Owner<String>,
        internal val loadOwner: DisplayBrowseLoadState.Owner,
    ) {
        val source: DisplayCategorySource
            get() = loadOwner.source
    }

    fun begin(
        loadIdentity: String,
        source: DisplayCategorySource,
    ): Owner {
        return Owner(
            publicationOwner = publicationGate.begin(loadIdentity),
            loadOwner = loadState.begin(source),
        )
    }

    fun invalidate() {
        publicationGate.invalidate()
        loadState.invalidate()
    }

    fun onSessionTokenChanged() {
        // The interceptor retries the same in-flight request with the refreshed token.
        // Keep its combined route and pager ownership so the retry can publish.
    }

    suspend fun loadNext(
        owner: Owner,
        limit: Int,
        openPager: suspend () -> DisplayPhotoPager,
    ): DisplayBrowseLoadState.Page? {
        return loadState.loadNext(owner.loadOwner, limit, openPager)
    }

    fun publish(owner: Owner, publication: () -> Unit): Boolean {
        var published = false
        publicationGate.publish(owner.publicationOwner) {
            published = loadState.publish(owner.loadOwner, publication)
        }
        return published
    }
}
