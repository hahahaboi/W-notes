package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import kotlin.math.abs

/**
 * Authoritative owner of annotation state logic.
 * Handles shifting, re-anchoring, and lifecycle of ContextAnnotations.
 */
object WAnnotationManager {

    /**
     * Updates annotation ranges after a text insertion in a specific block.
     * Boundary Affinity Policy:
     * - insertion at startOffset -> outside (moves forward)
     * - insertion at endOffset -> outside (stays put)
     * - insertion strictly inside -> expands
     */
    fun shiftAnnotationsForInsertion(
        annotations: List<ContextAnnotation>,
        blockId: String,
        insertOffset: Int,
        length: Int
    ): List<ContextAnnotation> {
        return annotations.map { annotation ->
            if (annotation.blockId != blockId) return@map annotation

            val start = annotation.startOffset
            val end = annotation.endOffset

            when {
                // Insertion is at or before the start: move entire annotation forward
                insertOffset <= start -> {
                    annotation.copy(
                        startOffset = start + length,
                        endOffset = end + length,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                // Insertion is strictly inside: expand end only
                insertOffset < end -> {
                    annotation.copy(
                        endOffset = end + length,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                // Insertion is at or after end: do nothing
                else -> annotation
            }
        }
    }

    /**
     * Updates annotation ranges after a text deletion in a specific block.
     */
    fun shiftAnnotationsForDeletion(
        annotations: List<ContextAnnotation>,
        blockId: String,
        deleteStart: Int,
        deleteEnd: Int
    ): List<ContextAnnotation> {
        val deleteLength = deleteEnd - deleteStart
        return annotations.mapNotNull { annotation ->
            if (annotation.blockId != blockId) return@mapNotNull annotation

            val start = annotation.startOffset
            val end = annotation.endOffset

            when {
                // 1. Entire deletion is before the annotation: shift both
                deleteEnd <= start -> {
                    annotation.copy(
                        startOffset = start - deleteLength,
                        endOffset = end - deleteLength,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                // 2. Entire deletion is after the annotation: do nothing
                deleteStart >= end -> annotation
                
                // 3. Deletion covers the entire annotation: remove it
                deleteStart <= start && deleteEnd >= end -> null
                
                // 4. Partial overlap or deletion strictly inside
                else -> {
                    // Calculation of intersection length with the annotation range
                    val intersectionStart = maxOf(deleteStart, start)
                    val intersectionEnd = minOf(deleteEnd, end)
                    val overlapLength = intersectionEnd - intersectionStart

                    var newStart = start
                    val newEnd = end - overlapLength

                    if (deleteStart <= start) {
                        // Deletion started before/at start: pull start to deleteStart
                        newStart = deleteStart
                    }

                    if (newStart < newEnd) {
                        annotation.copy(
                            startOffset = newStart,
                            endOffset = newEnd,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else null
                }
            }
        }
    }

    /**
     * Handles block splitting.
     */
    fun splitAnnotations(
        annotations: List<ContextAnnotation>,
        oldBlockId: String,
        newBlockId: String,
        splitOffset: Int
    ): List<ContextAnnotation> {
        return annotations.mapNotNull { annotation ->
            if (annotation.blockId != oldBlockId) return@mapNotNull annotation

            val start = annotation.startOffset
            val end = annotation.endOffset

            when {
                // Entirely in the head (old block)
                end <= splitOffset -> annotation
                // Entirely in the tail (new block)
                start >= splitOffset -> {
                    annotation.copy(
                        blockId = newBlockId,
                        startOffset = start - splitOffset,
                        endOffset = end - splitOffset,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                // Spans the split point. V1 Constraint: Truncate to one block.
                // Keep the larger segment.
                else -> {
                    val headLen = splitOffset - start
                    val tailLen = end - splitOffset
                    if (headLen >= tailLen) {
                        // Keep in head
                        annotation.copy(
                            endOffset = splitOffset,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        // Move to tail
                        annotation.copy(
                            blockId = newBlockId,
                            startOffset = 0,
                            endOffset = tailLen,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

    /**
     * Handles block merging.
     */
    fun mergeAnnotations(
        annotations: List<ContextAnnotation>,
        mergedBlockId: String,
        sourceBlockId: String,
        offsetShift: Int
    ): List<ContextAnnotation> {
        return annotations.map { annotation ->
            if (annotation.blockId == sourceBlockId) {
                annotation.copy(
                    blockId = mergedBlockId,
                    startOffset = annotation.startOffset + offsetShift,
                    endOffset = annotation.endOffset + offsetShift,
                    updatedAt = System.currentTimeMillis()
                )
            } else annotation
        }
    }

    /**
     * Fallback mechanism to verify/relocate annotations if offsets drift.
     */
    fun verifyAndRelocate(
        annotations: List<ContextAnnotation>,
        blocks: List<WBlock>
    ): List<ContextAnnotation> {
        return annotations.map { annotation ->
            val block = blocks.find { it.id == annotation.blockId } as? WTextContainer ?: return@map annotation
            val currentText = try {
                block.text.substring(annotation.startOffset.coerceIn(0, block.text.length), annotation.endOffset.coerceIn(0, block.text.length))
            } catch (e: Exception) {
                null
            }

            if (currentText == annotation.anchorText) {
                annotation
            } else {
                // drift recovery
                val matches = findAllOccurrences(block.text, annotation.anchorText)
                if (matches.isEmpty()) return@map annotation // Or mark invalid

                // Find closest match to original position
                val bestMatch = matches.minByOrNull { abs(it - annotation.startOffset) }
                if (bestMatch != null) {
                    annotation.copy(
                        startOffset = bestMatch,
                        endOffset = bestMatch + annotation.anchorText.length,
                        updatedAt = System.currentTimeMillis()
                    )
                } else annotation
            }
        }
    }

    private fun findAllOccurrences(text: String, sub: String): List<Int> {
        if (sub.isEmpty()) return emptyList()
        val indices = mutableListOf<Int>()
        var index = text.indexOf(sub)
        while (index >= 0) {
            indices.add(index)
            index = text.indexOf(sub, index + 1)
        }
        return indices
    }

    /**
     * Lookup methods.
     */
    fun getAnnotationsForBlock(annotations: List<ContextAnnotation>, blockId: String): List<ContextAnnotation> {
        return annotations.filter { it.blockId == blockId }
    }

    fun getAnnotationAtPoint(
        annotations: List<ContextAnnotation>,
        blockId: String,
        offset: Int
    ): ContextAnnotation? {
        return annotations.find { it.blockId == blockId && offset >= it.startOffset && offset < it.endOffset }
    }

    fun getAnnotationsIntersecting(
        annotations: List<ContextAnnotation>,
        blockId: String,
        start: Int,
        end: Int
    ): List<ContextAnnotation> {
        return annotations.filter { it.blockId == blockId && it.startOffset < end && it.endOffset > start }
    }

    /**
     * Adjusts annotations during a text replacement in a specific block.
     * Replacement is defined as deleting [deleteStart, deleteEnd) and inserting text of [insertLength].
     */
    fun replaceAnnotations(
        annotations: List<ContextAnnotation>,
        blockId: String,
        deleteStart: Int,
        deleteEnd: Int,
        insertLength: Int
    ): List<ContextAnnotation> {
        val deleteLength = deleteEnd - deleteStart
        val delta = insertLength - deleteLength

        return annotations.mapNotNull { annotation ->
            if (annotation.blockId != blockId) return@mapNotNull annotation

            val start = annotation.startOffset
            val end = annotation.endOffset

            when {
                // strictly before the replacement
                end <= deleteStart -> annotation
                // strictly after the replacement
                start >= deleteEnd -> annotation.copy(
                    startOffset = start + delta,
                    endOffset = end + delta,
                    updatedAt = System.currentTimeMillis()
                )
                // replacement covers the entire annotation
                deleteStart <= start && deleteEnd >= end -> {
                    // "Stretch" annotation to cover the new text if it was exactly the target
                    if (deleteStart == start && deleteEnd == end && insertLength > 0) {
                        annotation.copy(
                            endOffset = start + insertLength,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else null // invalidated
                }
                // replacement is strictly inside
                deleteStart >= start && deleteEnd <= end -> {
                    annotation.copy(
                        endOffset = end + delta,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                // partial overlaps
                else -> {
                    val newStart = if (start < deleteStart) start else deleteStart
                    val newEnd = if (end > deleteEnd) end + delta else deleteStart + insertLength
                    
                    if (newStart < newEnd) {
                        annotation.copy(
                            startOffset = newStart,
                            endOffset = newEnd,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else null
                }
            }
        }
    }
}
