package com.fnphoto.tv.browse

import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DisplaySearchRequestFactoryTest {
    private val gson = Gson()

    @Test
    fun forTag_serializesExactWebFilterBody() {
        assertEquals(
            """{"keyword":"","filters":[{"filterName":"photo_tag","filterValue":"旅行"}],"antiFilters":[]}""",
            gson.toJson(DisplaySearchRequestFactory.forTag("旅行")),
        )
    }

    @Test
    fun forMediaType_serializesExactWebFilterBody() {
        assertEquals(
            """{"keyword":"","filters":[{"filterName":"file_type","filterValue":"video"}],"antiFilters":[]}""",
            gson.toJson(DisplaySearchRequestFactory.forMediaType("video")),
        )
    }

    @Test
    fun forTag_preservesRawTrailingWhitespaceInFilterValue() {
        assertEquals(
            """{"keyword":"","filters":[{"filterName":"photo_tag","filterValue":"旅行 "}],"antiFilters":[]}""",
            gson.toJson(DisplaySearchRequestFactory.forTag("旅行 ")),
        )
    }

    @Test
    fun factories_rejectBlankMachineValues() {
        assertFailsWith<IllegalArgumentException> { DisplaySearchRequestFactory.forTag(" ") }
        assertFailsWith<IllegalArgumentException> { DisplaySearchRequestFactory.forMediaType(" ") }
    }
}
