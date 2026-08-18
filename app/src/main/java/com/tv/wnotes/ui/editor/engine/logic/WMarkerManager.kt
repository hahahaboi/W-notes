package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.data.local.entity.TextMarker
import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import kotlin.math.abs

/**
 * Logic for managing Text Markers, especially range shifting during mutations.
 * Supports markers spanning multiple blocks.
 */
object WMarkerManager {

    fun shiftMarkersForInsertion(
        markers: List<TextMarker>,
        blockId: String,
        insertOffset: Int,
        length: Int
    ): List<TextMarker> {
        return markers.map { marker ->
            var newStartOffset = marker.startOffset
            var newEndOffset = marker.endOffset

            if (marker.startBlockId == blockId) {
                if (insertOffset <= marker.startOffset) {
                    newStartOffset += length
                }
            }

            if (marker.endBlockId == blockId) {
                if (insertOffset <= marker.endOffset) {
                    newEndOffset += length
                }
            }

            if (newStartOffset != marker.startOffset || newEndOffset != marker.endOffset) {
                marker.copy(startOffset = newStartOffset, endOffset = newEndOffset)
            } else {
                marker
            }
        }
    }

    fun shiftMarkersForDeletion(
        markers: List<TextMarker>,
        blockId: String,
        deleteStart: Int,
        deleteEnd: Int,
        blocks: List<WBlock>
    ): List<TextMarker> {
        val deleteLength = deleteEnd - deleteStart
        return markers.mapNotNull { marker ->
            var newStartOffset = marker.startOffset
            var newEndOffset = marker.endOffset
            var startDeleted = false
            var endDeleted = false

            if (marker.startBlockId == blockId) {
                if (deleteEnd <= marker.startOffset) {
                    newStartOffset -= deleteLength
                } else if (deleteStart <= marker.startOffset) {
                    startDeleted = true
                }
            }

            if (marker.endBlockId == blockId) {
                if (deleteEnd <= marker.endOffset) {
                    newEndOffset -= deleteLength
                } else if (deleteStart < marker.endOffset) {
                    endDeleted = true
                }
            }
            
            // Invalidation
            if (startDeleted || endDeleted) return@mapNotNull null

            if (marker.startBlockId == marker.endBlockId && blockId == marker.startBlockId) {
                if (newStartOffset >= newEndOffset) return@mapNotNull null
            }

            if (newStartOffset != marker.startOffset || newEndOffset != marker.endOffset) {
                marker.copy(startOffset = newStartOffset, endOffset = newEndOffset)
            } else {
                marker
            }
        }
    }

    fun splitMarkers(
        markers: List<TextMarker>,
        oldBlockId: String,
        newBlockId: String,
        splitOffset: Int
    ): List<TextMarker> {
        return markers.map { marker ->
            var newStartBlockId = marker.startBlockId
            var newStartOffset = marker.startOffset
            var newEndBlockId = marker.endBlockId
            var newEndOffset = marker.endOffset

            if (marker.startBlockId == oldBlockId && marker.startOffset >= splitOffset) {
                newStartBlockId = newBlockId
                newStartOffset -= splitOffset
            }

            if (marker.endBlockId == oldBlockId && marker.endOffset >= splitOffset) {
                newEndBlockId = newBlockId
                newEndOffset -= splitOffset
            }

            if (newStartBlockId != marker.startBlockId || newStartOffset != marker.startOffset ||
                newEndBlockId != marker.endBlockId || newEndOffset != marker.endOffset
            ) {
                marker.copy(
                    startBlockId = newStartBlockId,
                    startOffset = newStartOffset,
                    endBlockId = newEndBlockId,
                    endOffset = newEndOffset
                )
            } else {
                marker
            }
        }
    }

    fun mergeMarkers(
        markers: List<TextMarker>,
        mergedBlockId: String,
        sourceBlockId: String,
        offsetShift: Int
    ): List<TextMarker> {
        return markers.map { marker ->
            var newStartBlockId = marker.startBlockId
            var newStartOffset = marker.startOffset
            var newEndBlockId = marker.endBlockId
            var newEndOffset = marker.endOffset

            if (marker.startBlockId == sourceBlockId) {
                newStartBlockId = mergedBlockId
                newStartOffset += offsetShift
            }

            if (marker.endBlockId == sourceBlockId) {
                newEndBlockId = mergedBlockId
                newEndOffset += offsetShift
            }

            if (newStartBlockId != marker.startBlockId || newEndBlockId != marker.endBlockId) {
                marker.copy(
                    startBlockId = newStartBlockId,
                    startOffset = newStartOffset,
                    endBlockId = newEndBlockId,
                    endOffset = newEndOffset
                )
            } else {
                marker
            }
        }
    }

    fun sortMarkers(markers: List<TextMarker>, blocks: List<WBlock>): List<TextMarker> {
        val blockOrder = blocks.withIndex().associate { it.value.id to it.index }
        return markers.sortedWith { m1, m2 ->
            val b1 = blockOrder[m1.startBlockId] ?: Int.MAX_VALUE
            val b2 = blockOrder[m2.startBlockId] ?: Int.MAX_VALUE
            if (b1 != b2) b1 - b2
            else m1.startOffset - m2.startOffset
        }
    }
}
