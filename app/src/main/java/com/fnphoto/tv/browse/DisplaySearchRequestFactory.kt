package com.fnphoto.tv.browse

internal object DisplaySearchRequestFactory {
    fun forTag(name: String): DisplaySearchRequest {
        require(name.isNotBlank())
        return DisplaySearchRequest(
            filters = listOf(DisplaySearchFilter(filterName = "photo_tag", filterValue = name)),
        )
    }

    fun forMediaType(fileType: String): DisplaySearchRequest {
        require(fileType.isNotBlank())
        return DisplaySearchRequest(
            filters = listOf(DisplaySearchFilter(filterName = "file_type", filterValue = fileType)),
        )
    }
}
