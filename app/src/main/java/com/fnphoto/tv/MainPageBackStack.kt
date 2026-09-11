package com.fnphoto.tv

internal class MainPageBackStack<Route : Any, Focus : Any> {
    private val entries = ArrayDeque<Entry<Route, Focus>>()
    private var currentRoute: Route? = null

    val canGoBack: Boolean
        get() = entries.isNotEmpty()

    fun resetTo(route: Route) {
        entries.clear()
        currentRoute = route
    }

    fun pushCurrent(currentRoute: Route, focus: Focus?, nextRoute: Route): Boolean {
        if (currentRoute == nextRoute) {
            this.currentRoute = currentRoute
            return false
        }
        entries.addLast(Entry(currentRoute, focus))
        this.currentRoute = nextRoute
        return true
    }

    fun popPrevious(): Entry<Route, Focus>? {
        val previous = entries.removeLastOrNull() ?: return null
        currentRoute = previous.route
        return previous
    }

    data class Entry<Route : Any, Focus : Any>(
        val route: Route,
        val focus: Focus?,
    )
}
