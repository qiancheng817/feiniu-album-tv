package com.fnphoto.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class MainCollectionFocusPolicyTest {
    @Test
    fun findsParentCollectionItemForNestedReturn() {
        val items = listOf(
            MainCollectionFocusItem(type = "place", id = "china:beijing"),
            MainCollectionFocusItem(type = "place", id = "china:chengdu"),
            MainCollectionFocusItem(type = "place", id = "china:shanghai"),
        )
        val target = MainCollectionFocusTarget(type = "place", id = "china:chengdu")

        assertEquals(1, MainCollectionFocusPolicy.findItemIndex(items, target))
    }

    @Test
    fun collectionGridIndexIncludesHeader() {
        assertEquals(2, MainCollectionFocusPolicy.lazyGridIndexForItemIndex(1))
    }

    @Test
    fun missingParentCollectionItemDoesNotRequestFocus() {
        val items = listOf(
            MainCollectionFocusItem(type = "place", id = "china:beijing"),
            MainCollectionFocusItem(type = "place", id = "china:shanghai"),
        )
        val target = MainCollectionFocusTarget(type = "place", id = "china:chengdu")

        assertEquals(-1, MainCollectionFocusPolicy.findItemIndex(items, target))
    }

    @Test
    fun supportsAllCollectionTypesThatOpenNestedPages() {
        val nestedTypes = listOf("album", "shared_album", "folder", "person", "place", "smart_category")
        val items = nestedTypes.mapIndexed { index, type ->
            MainCollectionFocusItem(type = type, id = "item-$index")
        }

        nestedTypes.forEachIndexed { index, type ->
            val target = MainCollectionFocusTarget(type = type, id = "item-$index")

            assertEquals(index, MainCollectionFocusPolicy.findItemIndex(items, target))
        }
    }
}
