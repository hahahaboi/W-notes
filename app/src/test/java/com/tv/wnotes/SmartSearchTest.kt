package com.tv.wnotes

import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.util.SmartSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSearchTest {
    private val notes = listOf(
        Note(id = 1, title = "Dog Note", content = "I have three dogs."),
        Note(id = 2, title = "Cat Note", content = "Cats are cool."),
        Note(id = 3, title = "Long Note", content = "This is a note with many words to test the word count search feature.")
    )

    @Test
    fun testKeywordSearch() {
        val results = SmartSearchEngine.filter(notes, "dog")
        assertEquals(1, results.size)
        assertEquals("Dog Note", results[0].title)
    }

    @Test
    fun testMentionPrompt() {
        val results = SmartSearchEngine.filter(notes, "mention dogs")
        assertEquals(1, results.size)
        assertEquals("Dog Note", results[0].title)
    }

    @Test
    fun testWordCountSearch() {
        // "This is a note with many words to test the word count search feature." -> 14 words
        val results = SmartSearchEngine.filter(notes, "10 words")
        assertEquals(1, results.size)
        assertEquals("Long Note", results[0].title)
    }
}
