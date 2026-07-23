package com.tv.wnotes.ui.components

import com.tv.wnotes.data.local.entity.Note

enum class SectionSortOption(val label: String) {
    RECENTLY_EDITED("Recently Edited"),
    RECENTLY_CREATED("Recently Created"),
    TITLE("Title A-Z"),
    WORD_COUNT("Word Count"),
    FOLDER("Folder"),
    STARRED_FIRST("Starred First");

    companion object {
        fun fromName(name: String?): SectionSortOption {
            return entries.firstOrNull { it.name == name } ?: RECENTLY_EDITED
        }
    }
}

fun sortNotes(
    notes: List<Note>,
    sortOption: SectionSortOption,
    folderNameForId: (Long?) -> String?
): List<Note> {
    val pinned = notes.filter { it.isPinned }
    val regular = notes.filterNot { it.isPinned }
    val sortedRegular = when (sortOption) {
        SectionSortOption.RECENTLY_EDITED -> regular.sortedByDescending { it.updatedAt }
        SectionSortOption.RECENTLY_CREATED -> regular.sortedByDescending { it.createdAt }
        SectionSortOption.TITLE -> regular.sortedBy { it.title.lowercase() }
        SectionSortOption.WORD_COUNT -> regular.sortedByDescending {
            if (it.content.isBlank()) 0 else it.content.trim().split(Regex("\\s+")).size
        }
        SectionSortOption.FOLDER -> regular.sortedWith(
            compareBy<Note> { folderNameForId(it.folderId).orEmpty().lowercase() }
                .thenByDescending { it.updatedAt }
        )
        SectionSortOption.STARRED_FIRST -> regular.sortedWith(
            compareByDescending<Note> { it.isStarred }.thenByDescending { it.updatedAt }
        )
    }
    return pinned.sortedByDescending { it.updatedAt } + sortedRegular
}
