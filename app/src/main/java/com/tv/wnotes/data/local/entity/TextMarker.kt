package com.tv.wnotes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "text_markers",
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
data class TextMarker(
    @PrimaryKey 
    val id: String = UUID.randomUUID().toString(),
    val tabId: Long,
    val startBlockId: String,
    val startOffset: Int,
    val endBlockId: String,
    val endOffset: Int,
    val anchorText: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis(),
    val type: MarkerType = MarkerType.DEFAULT
)

enum class MarkerType {
    DEFAULT, IMPORTANT, TODO, REFERENCE
}
