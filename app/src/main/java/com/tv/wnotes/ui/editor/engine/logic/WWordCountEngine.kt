package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer

/**
 * Optimized word counting for WEditor documents.
 * Avoids expensive Regex and String split operations.
 */
object WWordCountEngine {
    
    /**
     * Counts words in a list of blocks.
     * $O(N)$ where N is the total number of characters in the document.
     * Zero allocations for the count itself.
     */
    fun countWords(blocks: List<WBlock>): Int {
        var total = 0
        for (block in blocks) {
            if (block is WTextContainer) {
                total += countWordsInString(block.text)
            }
        }
        return total
    }

    private fun countWordsInString(text: String): Int {
        if (text.isEmpty()) return 0
        var count = 0
        var inWord = false
        
        for (i in text.indices) {
            val c = text[i]
            if (c.isWhitespace()) {
                if (inWord) {
                    count++
                    inWord = false
                }
            } else {
                inWord = true
            }
        }
        
        if (inWord) count++
        return count
    }
}
