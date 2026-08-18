package com.tv.wnotes.data.local.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.entity.NoteGroup
import com.tv.wnotes.data.local.entity.NoteGroupMember

data class GroupWithNotes(
    @Embedded val group: NoteGroup,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteGroupMember::class,
            parentColumn = "groupId",
            entityColumn = "noteId"
        )
    )
    val notes: List<Note>,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val members: List<NoteGroupMember>
) {
    val orderedNotes: List<Note>
        get() {
            val memberMap = members.associateBy { it.noteId }
            return notes.sortedBy { memberMap[it.id]?.orderIndex ?: Int.MAX_VALUE }
        }
}
