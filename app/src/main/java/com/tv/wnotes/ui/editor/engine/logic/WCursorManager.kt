package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.state.WCursorState
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint

/**
 * Single source of truth for cursor placement and management.
 * Converts hit-test results into cursor state.
 */
object WCursorManager {

    /**
     * Creates a new cursor state from a hit-test result.
     */
    fun createCursorFromHit(hit: WHitResult): WCursorState {
        return WCursorState(
            position = WSelectionPoint(
                blockId = hit.blockId,
                offset = hit.characterIndex
            ),
            affinity = hit.affinity,
            blinkVisible = true,
            isVisible = true
        )
    }

    /**
     * Toggles the blink visibility of the cursor.
     */
    fun toggleBlink(current: WCursorState): WCursorState {
        return current.copy(blinkVisible = !current.blinkVisible)
    }

    /**
     * Resets the blink state (e.g. after a move) to ensure the cursor is immediately visible.
     */
    fun resetBlink(current: WCursorState): WCursorState {
        return current.copy(blinkVisible = true)
    }
}
