package com.tv.wnotes.ui.editor.engine.state

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.logic.CursorAffinity

@Immutable
data class WSelectionState(
    val ranges: List<WSelectionRange> = emptyList(),
    val isActive: Boolean = false,
    val mode: WSelectionMode = WSelectionMode.NONE,
    val composingRange: WSelectionRange? = null,
    val startHandle: WSelectionHandleState = WSelectionHandleState(WSelectionHandleType.START),
    val endHandle: WSelectionHandleState = WSelectionHandleState(WSelectionHandleType.END),
    val activeHandle: WSelectionHandleType? = null
)

enum class WSelectionMode {
    NONE,
    CHARACTER,
    WORD,
    PARAGRAPH
}

@Immutable
data class WSelectionRange(
    val anchor: WSelectionPoint,
    val focus: WSelectionPoint
)

@Immutable
data class WSelectionPoint(
    val blockId: String,
    val offset: Int
)

@Immutable
data class WCursorState(
    val position: WSelectionPoint,
    val affinity: CursorAffinity = CursorAffinity.AFTER,
    val blinkVisible: Boolean = true,
    val isVisible: Boolean = true,
    val drawRect: Rect = Rect.Zero
)

enum class WDragPhase {
    IDLE,
    PREPARING_LIFT,
    LIFTING,
    DRAGGING,
    DROPPED,
    CANCELLED
}

@Immutable
data class WInsertionCaretState(
    val isVisible: Boolean = false,
    val insertionPoint: WSelectionPoint? = null,
    val drawRect: Rect = Rect.Zero,
    val lineIndex: Int = -1,
    val affinity: CursorAffinity = CursorAffinity.AFTER,
    val isValidTarget: Boolean = true,
    val virtualLineCount: Int = 0,
    val isVirtual: Boolean = false
)

@Immutable
data class WSmartWhitespaceTelemetry(
    val whitespaceBefore: String = "",
    val whitespaceAfter: String = "",
    val beginningOfBlock: Boolean = false,
    val endOfBlock: Boolean = false,
    val betweenWords: Boolean = false,
    val adjacentPunctuation: Boolean = false,
    val adjacentNewline: Boolean = false,
    val insertedLeadingSpace: Boolean = false,
    val insertedTrailingSpace: Boolean = false,
    val removedDuplicateSpaces: Boolean = false,
    val finalDestinationOffset: Int = -1
)

@Immutable
data class WTelemetryState(
    val currentStage: Int = 0,
    val pointerPosition: Offset = Offset.Unspecified,
    val dragPhase: WDragPhase = WDragPhase.IDLE,
    val hitTestResult: String = "None",
    val hoverBlock: String = "None",
    val hoverLine: Int = -1,
    val hoverOffset: Int = -1,
    val dropCaretVisible: Boolean = false,
    val dropCaretScreenPosition: Offset = Offset.Unspecified,
    val dropValid: Boolean = false,
    val virtualLineCount: Int = 0,
    val isVirtual: Boolean = false,
    val moveRangePending: Boolean = false,
    val moveRangeSuccess: Boolean = false,
    val failingStage: Int = -1,
    val failureReason: String? = null,
    val smartWhitespace: WSmartWhitespaceTelemetry = WSmartWhitespaceTelemetry()
)

@Immutable
data class WDragState(
    val phase: WDragPhase = WDragPhase.IDLE,
    val selectedRange: WSelectionRange? = null,
    val fingerPosition: Offset = Offset.Unspecified,
    val liftProgress: Float = 0f,
    val previewOffset: Offset = Offset.Zero,
    val preview: WDragPreviewState = WDragPreviewState(),
    val insertionCaret: WInsertionCaretState = WInsertionCaretState()
)

@Immutable
data class WViewportState(
    val visibleBlockIds: Set<String> = emptySet(),
    val scrollOffset: Float = 0f
)

@Immutable
data class WClipboardState(
    val plainText: String = "",
    val blocks: List<WBlock> = emptyList()
)

@Immutable
data class WEditorSettings(
    val fontSize: Int = 16,
    val lineSpacing: Float = 1.2f,
    val showLineNumbers: Boolean = false
)
