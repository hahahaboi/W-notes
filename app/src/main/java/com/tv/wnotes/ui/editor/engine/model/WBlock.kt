package com.tv.wnotes.ui.editor.engine.model

import androidx.compose.runtime.Immutable

/**
 * Represents a single unit of content in the WEditor.
 */
@Immutable
sealed interface WBlock {
    val id: String
    val revision: Int
    val parentBlockId: String?
    val createdAt: Long
    val updatedAt: Long
    val metadata: Map<String, String>
    val isVisible: Boolean
    val style: WBlockStyle
}

/**
 * Interface for blocks that contain editable rich text.
 */
interface WTextContainer {
    val text: String
    val spans: List<WTextSpan>
    
    fun copyWithText(text: String, spans: List<WTextSpan>): WBlock
}

@Immutable
data class WTextBlock(
    override val id: String,
    override val revision: Int = 0,
    override val parentBlockId: String? = null,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val metadata: Map<String, String> = emptyMap(),
    override val isVisible: Boolean = true,
    override val style: WBlockStyle = WBlockStyle(),
    override val text: String = "",
    override val spans: List<WTextSpan> = emptyList(),
    val type: TextBlockType = TextBlockType.Body
) : WBlock, WTextContainer {
    override fun copyWithText(text: String, spans: List<WTextSpan>): WBlock {
        return this.copy(
            text = text, 
            spans = spans, 
            revision = revision + 1, 
            updatedAt = System.currentTimeMillis()
        )
    }
}

@Immutable
data class WMediaBlock(
    override val id: String,
    override val revision: Int = 0,
    override val parentBlockId: String? = null,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val metadata: Map<String, String> = emptyMap(),
    override val isVisible: Boolean = true,
    override val style: WBlockStyle = WBlockStyle(),
    val url: String,
    val mediaType: MediaType,
    val caption: String? = null,
    val widthPercent: Float = 1.0f
) : WBlock

@Immutable
data class WChecklistBlock(
    override val id: String,
    override val revision: Int = 0,
    override val parentBlockId: String? = null,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val metadata: Map<String, String> = emptyMap(),
    override val isVisible: Boolean = true,
    override val style: WBlockStyle = WBlockStyle(),
    override val text: String = "",
    override val spans: List<WTextSpan> = emptyList(),
    val isChecked: Boolean = false
) : WBlock, WTextContainer {
    override fun copyWithText(text: String, spans: List<WTextSpan>): WBlock {
        return this.copy(
            text = text, 
            spans = spans, 
            revision = revision + 1, 
            updatedAt = System.currentTimeMillis()
        )
    }
}

@Immutable
data class WDrawingBlock(
    override val id: String,
    override val revision: Int = 0,
    override val parentBlockId: String? = null,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val metadata: Map<String, String> = emptyMap(),
    override val isVisible: Boolean = true,
    override val style: WBlockStyle = WBlockStyle(),
    val strokes: List<WStroke> = emptyList()
) : WBlock

@Immutable
data class WStroke(
    val points: List<androidx.compose.ui.geometry.Offset> = emptyList(),
    val color: Long,
    val width: Float
)

@Immutable
data class WTextBoxBlock(
    override val id: String,
    override val revision: Int = 0,
    override val parentBlockId: String? = null,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val metadata: Map<String, String> = emptyMap(),
    override val isVisible: Boolean = true,
    override val style: WBlockStyle = WBlockStyle(isFloating = true),
    override val text: String = "",
    override val spans: List<WTextSpan> = emptyList()
) : WBlock, WTextContainer {
    override fun copyWithText(text: String, spans: List<WTextSpan>): WBlock {
        return this.copy(
            text = text,
            spans = spans,
            revision = revision + 1,
            updatedAt = System.currentTimeMillis()
        )
    }
}

@Immutable
data class WBlockStyle(
    val indentationLevel: Int = 0,
    val paddingBottom: Float = 0f,
    val alignment: String = "left",
    val isFloating: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = -1f, // -1 means wrap_content or auto
    val height: Float = -1f
)

enum class TextBlockType {
    H1, H2, H3, Body, Quote, Bullet, Numbered, Checklist, Code
}

enum class MediaType {
    Image, Video, Audio, File
}
