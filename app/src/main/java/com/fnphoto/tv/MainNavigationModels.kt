package com.fnphoto.tv

import com.fnphoto.tv.browse.DisplayTagIdentity

internal sealed class StreamSource(val action: String, val title: String) {
    data object Gallery : StreamSource("gallery", "图库")
    data object Recent : StreamSource("recent", "最近添加")
    data object Favorites : StreamSource("favorites", "收藏")
    data class Album(val albumId: Int, private val albumTitle: String) : StreamSource("albums", albumTitle)
    data class Folder(val folderPath: String, private val folderTitle: String) : StreamSource("folders", folderTitle)
    data class Person(val personId: Int, private val personTitle: String) : StreamSource("people", personTitle)
    data class Place(val country: String, val city: String, private val placeTitle: String) : StreamSource("places", placeTitle)
    data class SmartCategory(val keyword: String, private val categoryTitle: String) : StreamSource("smart", categoryTitle)
    data class Tag(val name: String, private val displayTitle: String) :
        StreamSource("tags", displayTitle)
    data class MediaType(
        val categoryCode: Int,
        val fileType: String,
        private val displayTitle: String,
    ) : StreamSource("media_types", displayTitle)

    companion object {
        fun gallery(): StreamSource = Gallery
        fun recent(): StreamSource = Recent
        fun favorites(): StreamSource = Favorites
        fun album(albumId: Int, title: String): StreamSource = Album(albumId, title)
        fun folder(folderPath: String, title: String): StreamSource = Folder(folderPath, title)
        fun person(personId: Int, title: String): StreamSource = Person(personId, title)
        fun place(country: String, city: String, title: String): StreamSource = Place(country, city, title)
        fun smartCategory(keyword: String, title: String): StreamSource = SmartCategory(keyword, title)
        fun tag(name: String, title: String): StreamSource = Tag(name, title)
        fun mediaType(categoryCode: Int, fileType: String, title: String): StreamSource =
            MediaType(categoryCode, fileType, title)
    }
}

internal sealed interface MainPageRoute {
    val action: String

    data object Gallery : MainPageRoute {
        override val action = "gallery"
    }

    data object Recent : MainPageRoute {
        override val action = "recent"
    }

    data object Favorites : MainPageRoute {
        override val action = "favorites"
    }

    data object Albums : MainPageRoute {
        override val action = "albums"
    }

    data object Folders : MainPageRoute {
        override val action = "folders"
    }

    data object People : MainPageRoute {
        override val action = "people"
    }

    data object Places : MainPageRoute {
        override val action = "places"
    }

    data object Shared : MainPageRoute {
        override val action = "shared"
    }

    data object Smart : MainPageRoute {
        override val action = "smart"
    }

    data object Tags : MainPageRoute {
        override val action = "tags"
    }

    data object MediaTypes : MainPageRoute {
        override val action = "media_types"
    }

    data object Settings : MainPageRoute {
        override val action = "settings"
    }

    data class AlbumPhotos(
        val albumId: Int,
        val title: String,
        val ownerAction: String,
    ) : MainPageRoute {
        override val action = ownerAction
    }

    data class FolderBrowser(
        val folderPath: String,
        val title: String,
    ) : MainPageRoute {
        override val action = "folders"
    }

    data class DirectPhotos(
        val source: StreamSource,
    ) : MainPageRoute {
        override val action = source.action
    }
}

internal object MainRouteResolver {
    fun topLevel(action: String): MainPageRoute? = when (action) {
        "gallery" -> MainPageRoute.Gallery
        "albums" -> MainPageRoute.Albums
        "folders" -> MainPageRoute.Folders
        "favorites" -> MainPageRoute.Favorites
        "recent" -> MainPageRoute.Recent
        "shared" -> MainPageRoute.Shared
        "people" -> MainPageRoute.People
        "places" -> MainPageRoute.Places
        "smart" -> MainPageRoute.Smart
        "tags" -> MainPageRoute.Tags
        "media_types" -> MainPageRoute.MediaTypes
        "settings" -> MainPageRoute.Settings
        else -> null
    }
}

internal data class DisplayBrowseRestoration(
    val parentRoute: MainPageRoute,
    val parentFocusTarget: MainCollectionFocusTarget,
    val route: MainPageRoute.DirectPhotos,
)

internal object DisplayBrowseRestorationResolver {
    fun forTag(rawName: String, displayTitle: String): DisplayBrowseRestoration? {
        if (rawName.isBlank()) {
            return null
        }
        val source = StreamSource.tag(rawName, displayTitle.ifBlank { rawName }) as StreamSource.Tag
        return DisplayBrowseRestoration(
            parentRoute = MainPageRoute.Tags,
            parentFocusTarget = MainCollectionFocusTarget("tag", source.collectionCardId()),
            route = MainPageRoute.DirectPhotos(source),
        )
    }

    fun forMediaType(
        categoryCode: Int,
        fileType: String,
        displayTitle: String,
    ): DisplayBrowseRestoration? {
        if (fileType.isBlank()) {
            return null
        }
        return DisplayBrowseRestoration(
            parentRoute = MainPageRoute.MediaTypes,
            parentFocusTarget = MainCollectionFocusTarget("media_type", "media:$categoryCode"),
            route = MainPageRoute.DirectPhotos(
                StreamSource.mediaType(
                    categoryCode,
                    fileType,
                    displayTitle.ifBlank { "媒体类型 $categoryCode" },
                ),
            ),
        )
    }
}

internal fun StreamSource.Tag.collectionCardId(): String {
    return DisplayTagIdentity.fromRaw(name)
}
