package com.tv.wnotes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_groups")
data class NoteGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val folderId: Long? = null,
    val isDeleted: Boolean = false,
    val isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
