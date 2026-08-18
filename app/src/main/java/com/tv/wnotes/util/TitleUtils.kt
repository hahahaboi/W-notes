package com.tv.wnotes.util

object TitleUtils {
    fun getShortTitle(title: String, wordCount: Int = 3): String {
        if (title.isBlank()) return "Untitled"
        val words = title.trim().split(Regex("\\s+"))
        return if (words.size <= wordCount) {
            title
        } else {
            words.take(wordCount).joinToString(" ")
        }
    }
}
