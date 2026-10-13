package com.tv.wnotes.ui.editor.engine.model

import androidx.compose.runtime.Immutable
import com.tv.wnotes.data.local.entity.ContextAnnotation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The immutable root state of a WEditor document.
 */
@Immutable
data class WDocument(
    val id: String,
    val blocks: ImmutableList<WBlock> = persistentListOf(),
    val revision: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: WMetadata = WMetadata()
)

@Immutable
data class WMetadata(
    val title: String = "",
    val lastModified: Long = System.currentTimeMillis(),
    val authorId: String? = null,
    val extra: Map<String, String> = emptyMap()
)

@Immutable
data class WGoal(
    val blockId: String,
    val offset: Int
)
