package com.tv.wnotes.util

import com.tv.wnotes.data.local.entity.Note

object TitleUtils {
    fun getShortTitle(note: Note, maxWords: Int = 3): String {
        return NoteDisplayUtils.getShortTitle(note, maxWords)
    }

    fun getShortTitle(title: String, wordCount: Int = 3): String {
        return NoteDisplayUtils.getShortTitle(title, wordCount)
    }
}
