package com.tv.wnotes.ui.home

import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.model.GroupWithNotes

sealed class HomeItem {
    data class NoteItem(val note: Note) : HomeItem()
    data class GroupItem(val groupWithNotes: GroupWithNotes) : HomeItem()

    val id: String
        get() = when (this) {
            is NoteItem -> "note_${note.id}"
            is GroupItem -> "group_${groupWithNotes.group.id}"
        }

    val timestamp: Long
        get() = when (this) {
            is NoteItem -> note.updatedAt
            is GroupItem -> groupWithNotes.group.createdAt
        }
}
