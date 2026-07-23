package com.tv.wnotes.ui.editor.engine.clipboard

import com.tv.wnotes.ui.editor.engine.model.*
import java.util.UUID

/**
 * Handles serialization and deserialization of rich content for the clipboard.
 * Designed for forward and backward compatibility via versioning.
 */
object WClipboardSerializer {
    
    private const val CURRENT_VERSION = 1
    private const val FIELD_SEPARATOR = "|||"
    private const val BLOCK_SEPARATOR = "###"

    /**
     * Serializes WClipboardContent into a transportable string.
     */
    fun serialize(content: WClipboardContent): String {
        val sb = StringBuilder()
        sb.append("WNOTES_V${content.version}$FIELD_SEPARATOR")
        
        content.blocks.forEach { block ->
            sb.append(serializeBlock(block))
            sb.append(BLOCK_SEPARATOR)
        }
        
        return sb.toString()
    }

    /**
     * Deserializes a string back into WClipboardContent.
     */
    fun deserialize(data: String): WClipboardContent? {
        if (!data.startsWith("WNOTES_V")) return null
        
        try {
            val parts = data.split(FIELD_SEPARATOR)
            val version = parts[0].removePrefix("WNOTES_V").toInt()
            
            if (version > CURRENT_VERSION) {
                // Future version handling - fallback to plain text if possible
            }
            
            val blockData = parts.getOrNull(1) ?: return null
            val rawBlocks = blockData.split(BLOCK_SEPARATOR).filter { it.isNotBlank() }
            
            val blocks = rawBlocks.mapNotNull { deserializeBlock(it) }
            
            return WClipboardContent(
                version = version,
                blocks = blocks,
                plainText = blocks.filterIsInstance<WTextContainer>().joinToString("\n") { it.text }
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun serializeBlock(block: WBlock): String {
        // Simple manual serialization for this phase.
        // Format: TYPE|TEXT|SPANS_JSON|METADATA_JSON
        return when (block) {
            is WTextBlock -> "TEXT|${block.text}|${serializeSpans(block.spans)}"
            is WChecklistBlock -> "CHECK|${block.text}|${block.isChecked}|${serializeSpans(block.spans)}"
            else -> "UNKNOWN"
        }
    }

    private fun deserializeBlock(data: String): WBlock? {
        val parts = data.split("|")
        val type = parts.getOrNull(0) ?: return null
        
        return when (type) {
            "TEXT" -> WTextBlock(
                id = UUID.randomUUID().toString(),
                text = parts.getOrNull(1) ?: "",
                spans = deserializeSpans(parts.getOrNull(2) ?: "")
            )
            "CHECK" -> WChecklistBlock(
                id = UUID.randomUUID().toString(),
                text = parts.getOrNull(1) ?: "",
                isChecked = parts.getOrNull(2)?.toBoolean() ?: false,
                spans = deserializeSpans(parts.getOrNull(3) ?: "")
            )
            else -> null
        }
    }

    private fun serializeSpans(spans: List<WTextSpan>): String {
        // Format: start,end,type;...
        return spans.joinToString(";") { "${it.start},${it.end},${it.type.name}" }
    }

    private fun deserializeSpans(data: String): List<WTextSpan> {
        if (data.isBlank()) return emptyList()
        return data.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size < 3) return@mapNotNull null
            WTextSpan(
                start = parts[0].toInt(),
                end = parts[1].toInt(),
                type = SpanType.valueOf(parts[2])
            )
        }
    }
}
