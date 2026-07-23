package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.model.WTextSpan

/**
 * Internal helper for managing rich text spans during editing operations.
 * Ensures spans are shifted, resized, or removed correctly when text is modified.
 */
object WSpanManager {

    /**
     * Updates span ranges after a text insertion.
     */
    fun shiftSpansForInsertion(
        spans: List<WTextSpan>,
        insertOffset: Int,
        length: Int
    ): List<WTextSpan> {
        return spans.map { span ->
            when {
                // Span is strictly after insertion point
                span.start >= insertOffset -> {
                    span.copy(start = span.start + length, end = span.end + length)
                }
                // Insertion is inside the span
                insertOffset in (span.start + 1)..<span.end -> {
                    span.copy(end = span.end + length)
                }
                // Insertion is at exactly the start of the span
                // Standard behavior: push span forward
                span.start == insertOffset -> {
                    span.copy(start = span.start + length, end = span.end + length)
                }
                else -> span
            }
        }
    }

    /**
     * Updates span ranges after a text deletion.
     */
    fun shiftSpansForDeletion(
        spans: List<WTextSpan>,
        deleteStart: Int,
        deleteEnd: Int
    ): List<WTextSpan> {
        val deleteLength = deleteEnd - deleteStart
        return spans.mapNotNull { span ->
            val start = span.start
            val end = span.end

            when {
                // Span is strictly after deleted range
                start >= deleteEnd -> {
                    span.copy(start = start - deleteLength, end = end - deleteLength)
                }
                // Span is strictly before deleted range
                end <= deleteStart -> span
                // Span is completely covered by deleted range
                start >= deleteStart && end <= deleteEnd -> null
                // Deletion starts before and ends inside span
                start >= deleteStart && deleteEnd in start..<end -> {
                    span.copy(start = deleteStart, end = end - deleteLength)
                }
                // Deletion starts inside and ends after span
                start < deleteStart && deleteStart < end && deleteEnd >= end -> {
                    span.copy(end = deleteStart)
                }
                // Deletion is entirely inside the span
                start < deleteStart && end > deleteEnd -> {
                    span.copy(end = end - deleteLength)
                }
                else -> null
            }
        }.filter { it.start < it.end } // Cleanup empty spans
    }

    /**
     * Splits spans when a block is split into two.
     */
    fun splitSpans(
        spans: List<WTextSpan>,
        splitOffset: Int
    ): Pair<List<WTextSpan>, List<WTextSpan>> {
        val head = mutableListOf<WTextSpan>()
        val tail = mutableListOf<WTextSpan>()

        spans.forEach { span ->
            when {
                span.end <= splitOffset -> head.add(span)
                span.start >= splitOffset -> tail.add(
                    span.copy(
                        start = span.start - splitOffset,
                        end = span.end - splitOffset
                    )
                )
                else -> {
                    // Span crosses the split point - divide it
                    head.add(span.copy(end = splitOffset))
                    tail.add(
                        span.copy(
                            start = 0,
                            end = span.end - splitOffset
                        )
                    )
                }
            }
        }
        return head to tail
    }
}
