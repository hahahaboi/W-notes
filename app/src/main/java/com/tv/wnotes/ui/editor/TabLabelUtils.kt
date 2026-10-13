package com.tv.wnotes.ui.editor

/**
 * Derives the label for a tab based on its title and optional index.
 */
fun deriveTabLabel(title: String?, index: Int): String {
    if (!title.isNullOrBlank()) {
        return title
    }
    return "Tab ${index + 1}"
}
