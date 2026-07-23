package com.tv.wnotes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "link_paths")
data class LinkPath(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)
