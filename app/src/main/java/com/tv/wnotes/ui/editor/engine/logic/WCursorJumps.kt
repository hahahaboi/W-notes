package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint

/**
 * Word and paragraph cursor jumps.
 *
 * Text blocks are treated as one string joined by '\n', so a paragraph is either a block
 * or a newline-separated run inside a block. Non-text blocks are skipped.
 */
object WCursorJumps {

    private class FlatText(val text: String, val blockIds: List<String>, val blockStarts: List<Int>)

    private fun flatten(document: WDocument): FlatText {
        val ids = mutableListOf<String>()
        val starts = mutableListOf<Int>()
        val sb = StringBuilder()
        document.blocks.forEach { block ->
            val container = block as? WTextContainer ?: return@forEach
            if (ids.isNotEmpty()) sb.append('\n')
            ids.add(block.id)
            starts.add(sb.length)
            sb.append(container.text)
        }
        return FlatText(sb.toString(), ids, starts)
    }

    private fun FlatText.toGlobal(document: WDocument, point: WSelectionPoint): Int {
        val index = blockIds.indexOf(point.blockId)
        if (index != -1) {
            val blockEnd = if (index + 1 < blockStarts.size) blockStarts[index + 1] - 1 else text.length
            return (blockStarts[index] + point.offset).coerceIn(blockStarts[index], blockEnd)
        }
        // Cursor sits in a non-text block: use the start of the next text block.
        val blockIndex = document.blocks.indexOfFirst { it.id == point.blockId }
        val next = document.blocks.drop(blockIndex + 1).firstOrNull { it is WTextContainer }
        return next?.let { blockStarts[blockIds.indexOf(it.id)] } ?: text.length
    }

    private fun FlatText.toPoint(global: Int): WSelectionPoint? {
        if (blockIds.isEmpty()) return null
        val index = blockStarts.indexOfLast { it <= global }.coerceAtLeast(0)
        return WSelectionPoint(blockIds[index], global - blockStarts[index])
    }

    private fun Char.isWordChar() = isLetterOrDigit() || this == '_' || this == '\''

    private fun jump(document: WDocument, from: WSelectionPoint, step: (String, Int) -> Int): WSelectionPoint? {
        val flat = flatten(document)
        if (flat.blockIds.isEmpty()) return null
        val start = flat.toGlobal(document, from)
        return flat.toPoint(step(flat.text, start).coerceIn(0, flat.text.length))
    }

    /** Start of the next word. */
    fun nextWord(document: WDocument, from: WSelectionPoint) = jump(document, from) { text, start ->
        var i = start
        while (i < text.length && text[i].isWordChar()) i++
        while (i < text.length && !text[i].isWordChar()) i++
        i
    }

    /** Start of the current word, or of the previous one if already at a word start. */
    fun previousWord(document: WDocument, from: WSelectionPoint) = jump(document, from) { text, start ->
        var i = start
        while (i > 0 && !text[i - 1].isWordChar()) i--
        while (i > 0 && text[i - 1].isWordChar()) i--
        i
    }

    /** Start of the next non-empty paragraph, or the end of the document. */
    fun nextParagraph(document: WDocument, from: WSelectionPoint) = jump(document, from) { text, start ->
        val newline = text.indexOf('\n', start)
        if (newline == -1) {
            text.length
        } else {
            var i = newline
            while (i < text.length && text[i] == '\n') i++
            i
        }
    }

    /** Start of the current paragraph, or of the previous non-empty one if already at a paragraph start. */
    fun previousParagraph(document: WDocument, from: WSelectionPoint) = jump(document, from) { text, start ->
        var i = start
        while (i > 0 && text[i - 1] == '\n') i--
        while (i > 0 && text[i - 1] != '\n') i--
        i
    }
}
