package com.tv.wnotes.viewmodel

sealed class SelectionState {
    data object None : SelectionState()
    data class SingleNote(val noteId: Long) : SelectionState()
    data class MultipleNotes(val noteIds: Set<Long>) : SelectionState()
    data class SingleGroup(val groupId: Long) : SelectionState()
    data class MultipleItems(val noteIds: Set<Long>, val groupIds: Set<Long>) : SelectionState()

    val totalCount: Int
        get() = when (this) {
            is None -> 0
            is SingleNote -> 1
            is MultipleNotes -> noteIds.size
            is SingleGroup -> 1
            is MultipleItems -> noteIds.size + groupIds.size
        }

    val isSingleNote: Boolean get() = this is SingleNote
    val hasGroups: Boolean get() = this is SingleGroup || (this is MultipleItems && groupIds.isNotEmpty())
}
