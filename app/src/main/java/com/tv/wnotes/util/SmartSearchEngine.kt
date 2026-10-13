package com.tv.wnotes.util

import com.tv.wnotes.data.local.entity.Note

object SmartSearchEngine {

    fun filter(notes: List<Note>, query: String): List<Note> {
        if (query.isBlank()) return notes
        
        val trimmedQuery = query.trim().lowercase()

        // 1. Word Count Search
        // Patterns: "100", "100 words"
        val wordCountMatch = Regex("^(\\d+)(?:\\s*words?)?$").find(trimmedQuery)
        if (wordCountMatch != null) {
            val targetCount = wordCountMatch.groupValues[1].toInt()
            return notes.filter { note ->
                val wordCount = countWords(note.content)
                // "near or just over" -> within 10% below or any amount above? 
                // Let's interpret as: >= targetCount - (targetCount * 0.1) or simply notes that are closest to it.
                // User said "near or just over that limit" -> let's show notes >= targetCount but also those very close below.
                wordCount >= targetCount || (targetCount - wordCount) <= (targetCount * 0.1).toInt()
            }.sortedBy { (countWords(it.content) - targetCount).absoluteValue() }
        }

        // 2. Mention Prompt
        // Patterns: "mention dogs", "note where i mention dogs", "where i mention dogs"
        val mentionRegex = Regex("(?:note\\s+where\\s+i\\s+)?mention\\s+(.+)")
        val mentionMatch = mentionRegex.find(trimmedQuery)
        if (mentionMatch != null) {
            val keyword = mentionMatch.groupValues[1].trim()
            return notes.filter { 
                it.title.contains(keyword, ignoreCase = true) || 
                it.content.contains(keyword, ignoreCase = true) 
            }
        }

        // 3. Standard Search (Fallback)
        return notes.filter { 
            it.title.contains(trimmedQuery, ignoreCase = true) || 
            it.content.contains(trimmedQuery, ignoreCase = true) 
        }
    }

    private fun countWords(content: String): Int {
        if (content.isBlank()) return 0
        return content.trim().split(Regex("\\s+")).size
    }

    private fun Int.absoluteValue() = if (this < 0) -this else this
}
