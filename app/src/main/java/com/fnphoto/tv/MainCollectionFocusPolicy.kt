package com.fnphoto.tv

internal data class MainCollectionFocusItem(
    val type: String,
    val id: String,
)

internal data class MainCollectionFocusTarget(
    val type: String,
    val id: String,
)

internal object MainCollectionFocusPolicy {
    fun findItemIndex(items: List<MainCollectionFocusItem>, target: MainCollectionFocusTarget?): Int {
        if (target == null) {
            return -1
        }
        return items.indexOfFirst { item ->
            item.type == target.type && item.id == target.id
        }
    }

    fun lazyGridIndexForItemIndex(itemIndex: Int): Int {
        return if (itemIndex >= 0) itemIndex + 1 else -1
    }
}
