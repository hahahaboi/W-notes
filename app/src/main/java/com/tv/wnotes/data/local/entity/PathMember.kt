package com.tv.wnotes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "path_members")
data class PathMember(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pathId: Long,
    val noteId: Long? = null,
    val groupId: Long? = null,
    val orderIndex: Int
)
