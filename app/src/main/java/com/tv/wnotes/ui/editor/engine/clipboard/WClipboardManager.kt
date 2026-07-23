package com.tv.wnotes.ui.editor.engine.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager
import com.tv.wnotes.ui.editor.engine.logic.WSpanManager
import com.tv.wnotes.ui.editor.engine.logic.WContentManager
import com.tv.wnotes.ui.editor.engine.model.*
import com.tv.wnotes.ui.editor.engine.state.*
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID

/**
 * Manages all clipboard operations for WEditor.
 * Integrates with Android system clipboard and internal rich content model.
 */
class WClipboardManager(
    private val context: Context
) {
    private val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    // Internal cache for the last copied rich content from W Notes
    private var internalRichClipboard: WClipboardContent? = null

    /**
     * Copies current selection to both internal and system clipboard.
     */
    fun copy(snapshot: WEditorSnapshot) {
        val content = extractSelection(snapshot) ?: return
        internalRichClipboard = content
        
        val clip = ClipData.newPlainText("W Notes Content", content.plainText)
        // Note: Full implementation would use custom MIME type for rich data serialization
        systemClipboard.setPrimaryClip(clip)
    }

    /**
     * Cuts current selection.
     */
    fun cut(snapshot: WEditorSnapshot): WOperation.ClipboardOperation? {
        val content = extractSelection(snapshot) ?: return null
        internalRichClipboard = content
        
        val clip = ClipData.newPlainText("W Notes Content", content.plainText)
        systemClipboard.setPrimaryClip(clip)

        val afterSnapshot = WContentManager.deleteSelection(snapshot)
        
        return WOperation.ClipboardOperation(
            beforeSnapshot = snapshot,
            afterSnapshot = afterSnapshot,
            type = WOperation.ClipboardOperation.Type.Cut
        )
    }

    /**
     * Pastes content from clipboard.
     */
    fun paste(snapshot: WEditorSnapshot, asPlainText: Boolean = false): WOperation.ClipboardOperation? {
        val content = if (asPlainText) {
            getSystemPlainText()?.let { WClipboardContent(plainText = it) }
        } else {
            getRichContent()
        } ?: return null

        val afterSnapshot = executePaste(snapshot, content)
        
        return WOperation.ClipboardOperation(
            beforeSnapshot = snapshot,
            afterSnapshot = afterSnapshot,
            type = if (asPlainText) WOperation.ClipboardOperation.Type.PastePlainText else WOperation.ClipboardOperation.Type.Paste
        )
    }

    private fun getRichContent(): WClipboardContent? {
        val systemText = getSystemPlainText() ?: return null
        
        // If system text matches our internal rich cache, use the rich version.
        // Otherwise, it was copied from another app, so treat as plain text.
        return if (internalRichClipboard?.plainText == systemText) {
            internalRichClipboard
        } else {
            WClipboardContent(plainText = systemText)
        }
    }

    private fun getSystemPlainText(): String? {
        val clip = systemClipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            return clip.getItemAt(0).text?.toString()
        }
        return null
    }

    private fun extractSelection(snapshot: WEditorSnapshot): WClipboardContent? {
        if (snapshot.selection.ranges.isEmpty()) return null
        val range = WSelectionManager.normalize(snapshot.selection.ranges.first(), snapshot.document.blocks)
        val blocks = snapshot.document.blocks
        
        val startIdx = blocks.indexOfFirst { it.id == range.anchor.blockId }
        val endIdx = blocks.indexOfFirst { it.id == range.focus.blockId }
        
        if (startIdx == -1 || endIdx == -1) return null
        
        val extractedBlocks = mutableListOf<WBlock>()
        val plainTextBuilder = StringBuilder()

        for (i in startIdx..endIdx) {
            val block = blocks[i]
            if (block !is WTextContainer) continue
            
            val start = if (i == startIdx) range.anchor.offset else 0
            val end = if (i == endIdx) range.focus.offset else block.text.length
            
            val sliceText = block.text.substring(start, end)
            val sliceSpans = block.spans.filter { it.start < end && it.end > start }
                .map { it.copy(
                    start = (it.start - start).coerceAtLeast(0),
                    end = (it.end - start).coerceAtMost(sliceText.length)
                ) }

            val slicedBlock = block.copyWithText(sliceText, sliceSpans)
            extractedBlocks.add(slicedBlock)
            plainTextBuilder.append(sliceText)
            if (i < endIdx) plainTextBuilder.append("\n")
        }

        return WClipboardContent(
            blocks = extractedBlocks,
            plainText = plainTextBuilder.toString()
        )
    }

    private fun executePaste(snapshot: WEditorSnapshot, content: WClipboardContent): WEditorSnapshot {
        val cursor = snapshot.cursor.position
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == cursor.blockId }
        
        if (index == -1) return snapshot

        // For simplicity in this phase, we merge all pasted blocks into the current block 
        // if they are text-only, or replace current line if they are multi-block.
        
        if (content.blocks.isEmpty()) {
            // Paste raw text from other apps
            return WContentManager.insertText(snapshot, content.plainText)
        }

        if (content.blocks.size == 1) {
            val block = blocks[index] as? WTextContainer ?: return snapshot
            val pastedBlock = content.blocks.first() as WTextContainer
            
            val textToInsert = pastedBlock.text
            val newText = StringBuilder(block.text).insert(cursor.offset, textToInsert).toString()
            
            val shiftedOldSpans = WSpanManager.shiftSpansForInsertion(block.spans, cursor.offset, textToInsert.length)
            val adjustedPastedSpans = pastedBlock.spans.map { 
                it.copy(start = it.start + cursor.offset, end = it.end + cursor.offset) 
            }
            
            val updatedBlock = block.copyWithText(newText, (shiftedOldSpans + adjustedPastedSpans).toImmutableList())
            blocks[index] = updatedBlock
            
            val newPos = WSelectionPoint(blockId = cursor.blockId, offset = cursor.offset + textToInsert.length)
            return snapshot.copy(
                document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
                cursor = snapshot.cursor.copy(position = newPos),
                selection = WSelectionManager.collapseTo(newPos)
            )
        } else {
            // Multi-block paste: Split current block and insert the others
            // This is a simplified version of the logic
            val block = blocks[index] as? WTextBlock ?: return snapshot
            
            val headText = block.text.substring(0, cursor.offset)
            val tailText = block.text.substring(cursor.offset)
            
            val (headSpans, tailSpans) = WSpanManager.splitSpans(block.spans, cursor.offset)
            
            val headBlock = block.copy(text = headText, spans = headSpans, revision = block.revision + 1)
            val tailBlock = block.copy(id = UUID.randomUUID().toString(), text = tailText, spans = tailSpans)
            
            val intermediateBlocks = content.blocks.map { b ->
                // Generate fresh IDs for pasted blocks to avoid collisions
                if (b is WTextBlock) b.copy(id = UUID.randomUUID().toString())
                else if (b is WChecklistBlock) b.copy(id = UUID.randomUUID().toString())
                else b
            }
            
            blocks[index] = headBlock
            blocks.addAll(index + 1, intermediateBlocks)
            blocks.add(index + 1 + intermediateBlocks.size, tailBlock)
            
            val lastPasted = intermediateBlocks.last() as WTextContainer
            val newPos = WSelectionPoint(intermediateBlocks.last().id, lastPasted.text.length)
            
            return snapshot.copy(
                document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
                cursor = snapshot.cursor.copy(position = newPos),
                selection = WSelectionManager.collapseTo(newPos)
            )
        }
    }
}
