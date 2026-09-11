package com.fnphoto.tv

internal object MainTopNavigationFocusPolicy {
    enum class FocusScrollBehavior {
        None,
        Animated,
    }

    enum class TopNavTextTone {
        Primary,
        Secondary,
        Focus,
        Disabled,
    }

    fun shouldScrollBeforeFocus(selectedIndex: Int, visibleIndexes: List<Int>): Boolean {
        return focusScrollBehavior(selectedIndex, visibleIndexes) == FocusScrollBehavior.Animated
    }

    fun focusScrollBehavior(
        selectedIndex: Int,
        visibleIndexes: List<Int>,
        preScrollAllowed: Boolean = true,
    ): FocusScrollBehavior {
        if (!preScrollAllowed || selectedIndex < 0) {
            return FocusScrollBehavior.None
        }
        return if (visibleIndexes.isEmpty() || selectedIndex !in visibleIndexes) {
            FocusScrollBehavior.Animated
        } else {
            FocusScrollBehavior.None
        }
    }

    fun shouldShowUnderline(selected: Boolean, focused: Boolean): Boolean {
        return selected || focused
    }

    fun topNavTextTone(selected: Boolean, focused: Boolean, enabled: Boolean): TopNavTextTone {
        return when {
            focused -> TopNavTextTone.Focus
            selected -> TopNavTextTone.Primary
            !enabled -> TopNavTextTone.Disabled
            else -> TopNavTextTone.Secondary
        }
    }
}
