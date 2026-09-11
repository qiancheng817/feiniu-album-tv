package com.fnphoto.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainRouteResolverTest {
    @Test
    fun newDisplayActionsResolveToTypedRoutes() {
        assertEquals(MainPageRoute.Tags, MainRouteResolver.topLevel("tags"))
        assertEquals(MainPageRoute.MediaTypes, MainRouteResolver.topLevel("media_types"))
        assertNull(MainRouteResolver.topLevel("map"))
        assertNull(MainRouteResolver.topLevel("similar"))
        assertNull(MainRouteResolver.topLevel("recycle_bin"))
    }
}
