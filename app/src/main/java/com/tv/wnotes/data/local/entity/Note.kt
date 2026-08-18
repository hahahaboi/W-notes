package com.tv.wnotes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val folderId: Long? = null,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val isLocked: Boolean = false,
    val isRough: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
