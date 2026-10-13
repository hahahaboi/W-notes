package com.tv.wnotes.util

import com.tv.wnotes.data.local.entity.Note

data class NoteDisplayData(
    val displayTitle: String,
    val displayContent: String
)

object NoteDisplayUtils {

    fun resolveNoteDisplayData(note: Note, displayTitleOverride: String? = null): NoteDisplayData {
        // 1. Check override if supplied
        if (!displayTitleOverride.isNullOrBlank()) {
            val cleanOverride = cleanText(displayTitleOverride)
            if (cleanOverride.isNotBlank()) {
                return NoteDisplayData(
                    displayTitle = cleanOverride,
                    displayContent = cleanContentPreview(note.content, cleanOverride)
                )
            }
        }

        // 2. Check saved note title (note.title)
        val savedTitle = cleanText(note.title)
        val isPlaceholderTitle = savedTitle.isBlank() || 
            savedTitle.equals("Untitled", ignoreCase = true) || 
            savedTitle.equals("New Note", ignoreCase = true)

        if (!isPlaceholderTitle) {
            return NoteDisplayData(
                displayTitle = savedTitle,
                displayContent = cleanContentPreview(note.content, savedTitle)
            )
        }

        // 3. Fallback to note.content / body text
        val rawContent = note.content.trim()
        val cleanFullContent = cleanContentText(rawContent)

        if (cleanFullContent.isNotBlank()) {
            val lines = cleanFullContent.lines().map { it.trim() }.filter { it.isNotBlank() }
            val firstLine = lines.firstOrNull() ?: ""

            val titleText = if (firstLine.length > 30) {
                firstLine.take(30).trimEnd() + "..."
            } else {
                firstLine
            }

            val remainingContent = if (lines.size > 1) {
                lines.drop(1).joinToString("\n")
            } else {
                if (firstLine.length > 30) cleanFullContent else ""
            }

            return NoteDisplayData(
                displayTitle = titleText.ifBlank { "" },
                displayContent = remainingContent.ifBlank { "No preview available" }
            )
        }

        // 4. Fallback if both title and content are empty
        return NoteDisplayData(
            displayTitle = "",
            displayContent = "No preview available"
        )
    }

    fun getShortTitle(note: Note, maxWords: Int = 3): String {
        val displayData = resolveNoteDisplayData(note)
        val title = displayData.displayTitle
        val words = title.trim().split(Regex("\\s+"))
        return if (words.size <= maxWords) {
            title
        } else {
            words.take(maxWords).joinToString(" ")
        }
    }

    fun getShortTitle(titleStr: String, maxWords: Int = 3): String {
        val clean = cleanText(titleStr)
        if (clean.isBlank() || clean.equals("Untitled", ignoreCase = true) || clean.equals("New Note", ignoreCase = true)) {
            return ""
        }
        val words = clean.split(Regex("\\s+"))
        return if (words.size <= maxWords) {
            clean
        } else {
            words.take(maxWords).joinToString(" ")
        }
    }

    private fun cleanText(text: String): String {
        if (text.isBlank()) return ""
        var cleaned = text.trim()
        while (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.removePrefix("[").removeSuffix("]").trim()
        }
        return cleaned.replace("[", "").replace("]", "").trim()
    }

    private fun cleanContentText(rawContent: String): String {
        if (rawContent.isBlank()) return ""
        val lines = rawContent.lines().map { line ->
            var l = line.trim()
            while (l.startsWith("[") && l.contains("]")) {
                l = l.substringAfter("]").trim()
            }
            l
        }.filter { it.isNotBlank() }

        val cleaned = lines.joinToString("\n")
        if (cleaned.isNotBlank()) return cleaned

        return cleanText(rawContent)
    }

    private fun cleanContentPreview(rawContent: String, titleToExclude: String): String {
        val cleaned = cleanContentText(rawContent)
        if (cleaned.isBlank()) return "No preview available"
        if (cleaned.equals(titleToExclude, ignoreCase = true)) return "No preview available"
        return cleaned
    }
}
