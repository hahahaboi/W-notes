package com.tv.wnotes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "context_annotations",
    foreignKeys = [
        ForeignKey(
            entity = NoteTab::class,
            parentColumns = ["id"],
            childColumns = ["tabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tabId"])]
)
data class ContextAnnotation(
    @PrimaryKey 
    val id: String = UUID.randomUUID().toString(),
    val tabId: Long,
    val blockId: String,
    val startOffset: Int,
    val endOffset: Int,
    val anchorText: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: String? = null
)
