package com.tv.wnotes.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager
import com.tv.wnotes.ui.editor.engine.model.SpanType
import com.tv.wnotes.ui.editor.engine.model.TextBlockType
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.model.WBlock

@Composable
fun WFormattingToolbar(
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    // Font Sizes
    val fontSizes = listOf(6, 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 32, 36, 40, 48, 56, 64)
    var showFontSizeDropdown by remember { mutableStateOf(false) }

    // Font Families
    val fontFamilies = listOf("Default", "Serif", "Sans-Serif", "Monospace", "Cursive")
    var showFontFamilyDropdown by remember { mutableStateOf(false) }

    // Text Styles
    var showTextStyleDropdown by remember { mutableStateOf(false) }

    // Alignment
    var showAlignmentDropdown by remember { mutableStateOf(false) }

    val activeBlocks = remember(snapshot.selection, snapshot.document.blocks) {
        if (!snapshot.selection.isActive) {
            listOfNotNull(snapshot.document.blocks.find { it.id == snapshot.cursor.position.blockId })
        } else {
            snapshot.selection.ranges.flatMap { range ->
                val norm = WSelectionManager.normalize(range, snapshot.document.blocks)
                val s = snapshot.document.blocks.indexOfFirst { it.id == norm.anchor.blockId }
                val e = snapshot.document.blocks.indexOfFirst { it.id == norm.focus.blockId }
                if (s != -1 && e != -1 && s <= e) {
                    snapshot.document.blocks.subList(s, (e + 1).coerceAtMost(snapshot.document.blocks.size))
                } else {
                    emptyList()
                }
            }.distinctBy { it.id }
        }
    }

    // Inline styles present at the cursor (or at the start of the selection), to highlight their buttons.
    val activeSpanTypes = remember(snapshot.selection, snapshot.cursor.position, snapshot.document.blocks) {
        val point = snapshot.selection.ranges.firstOrNull()
            ?.takeIf { snapshot.selection.isActive }
            ?.let { WSelectionManager.normalize(it, snapshot.document.blocks).anchor }
            ?: snapshot.cursor.position
        val block = snapshot.document.blocks.find { it.id == point.blockId } as? WTextContainer
        block?.spans
            ?.filter { it.start <= point.offset && point.offset < it.end || (it.start < point.offset && point.offset == it.end) }
            ?.map { it.type }
            ?.toSet()
            ?: emptySet()
    }

    val currentAlignment = activeBlocks.firstOrNull()?.style?.alignment ?: "left"
    val currentBlockType = (activeBlocks.firstOrNull() as? com.tv.wnotes.ui.editor.engine.model.WTextBlock)?.type ?: TextBlockType.Body
    val isChecklist = activeBlocks.firstOrNull() is com.tv.wnotes.ui.editor.engine.model.WChecklistBlock

    val currentFontSize = remember(snapshot.selection, snapshot.document.blocks, snapshot.settings.fontSize) {
        val selection = snapshot.selection
        if (!selection.isActive || WSelectionManager.isCollapsed(selection)) {
            val point = selection.ranges.firstOrNull()?.focus ?: snapshot.cursor.position
            val block = snapshot.document.blocks.find { it.id == point.blockId } as? WTextContainer
            val span = block?.spans?.findLast { it.type == SpanType.FontSize && it.start <= point.offset && it.end >= point.offset }
            span?.attributes?.get("size")?.toIntOrNull() ?: snapshot.settings.fontSize
        } else {
            val ranges = selection.ranges.map { WSelectionManager.normalize(it, snapshot.document.blocks) }
            val sizes = mutableSetOf<Int>()
            ranges.forEach { range ->
                val startIdx = snapshot.document.blocks.indexOfFirst { it.id == range.anchor.blockId }
                val endIdx = snapshot.document.blocks.indexOfFirst { it.id == range.focus.blockId }
                if (startIdx != -1 && endIdx != -1 && startIdx <= endIdx) {
                    for (i in startIdx..endIdx) {
                        val block = snapshot.document.blocks.getOrNull(i) as? WTextContainer ?: continue
                        val start = if (i == startIdx) range.anchor.offset else 0
                        val end = if (i == endIdx) range.focus.offset else block.text.length
                        val spans = block.spans.filter { it.type == SpanType.FontSize && it.start < end && it.end > start }
                        if (spans.isEmpty()) sizes.add(snapshot.settings.fontSize)
                        else spans.forEach { span -> span.attributes["size"]?.toIntOrNull()?.let { sizes.add(it) } }
                    }
                }
            }
            if (sizes.size == 1) sizes.first() else null
        }
    }

    val currentFontFamily = remember(snapshot.selection, snapshot.document.blocks, snapshot.settings.fontFamily) {
        val selection = snapshot.selection
        if (!selection.isActive || WSelectionManager.isCollapsed(selection)) {
            val point = selection.ranges.firstOrNull()?.focus ?: snapshot.cursor.position
            val block = snapshot.document.blocks.find { it.id == point.blockId } as? WTextContainer
            val span = block?.spans?.findLast { it.type == SpanType.FontFamily && it.start <= point.offset && it.end >= point.offset }
            span?.attributes?.get("family") ?: snapshot.settings.fontFamily
        } else {
             // Simplified: just check first selected span
             val firstRange = selection.ranges.firstOrNull()
             if (firstRange != null) {
                 val point = WSelectionManager.normalize(firstRange, snapshot.document.blocks).anchor
                 val block = snapshot.document.blocks.find { it.id == point.blockId } as? WTextContainer
                 val span = block?.spans?.findLast { it.type == SpanType.FontFamily && it.start <= point.offset && it.end >= point.offset }
                 span?.attributes?.get("family") ?: snapshot.settings.fontFamily
             } else {
                 snapshot.settings.fontFamily
             }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 0. Undo / Redo
            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                onClick = { onCommand(WEditorCommand.Undo) }
            )
            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                onClick = { onCommand(WEditorCommand.Redo) }
            )

            ToolbarDivider(dividerColor)

            // 1. Checklist
            ToolbarIconButton(
                icon = Icons.Outlined.CheckBox,
                contentDescription = "Checklist",
                isActive = isChecklist,
                onClick = { onCommand(WEditorCommand.ToggleBlockType(TextBlockType.Checklist)) }
            )

            ToolbarDivider(dividerColor)

            // 2. Text Style (T)
            Box {
                ToolbarTextButton(
                    text = when(currentBlockType) {
                        TextBlockType.H1 -> "H1"
                        TextBlockType.H2 -> "H2"
                        TextBlockType.H3 -> "H3"
                        else -> "T"
                    },
                    onClick = { showTextStyleDropdown = true }
                )
                DropdownMenu(expanded = showTextStyleDropdown, onDismissRequest = { showTextStyleDropdown = false }) {
                    DropdownMenuItem(text = { Text("Normal Text") }, onClick = { onCommand(WEditorCommand.ToggleBlockType(TextBlockType.Body)); showTextStyleDropdown = false })
                    DropdownMenuItem(text = { Text("Heading 1") }, onClick = { onCommand(WEditorCommand.ToggleBlockType(TextBlockType.H1)); showTextStyleDropdown = false })
                    DropdownMenuItem(text = { Text("Heading 2") }, onClick = { onCommand(WEditorCommand.ToggleBlockType(TextBlockType.H2)); showTextStyleDropdown = false })
                    DropdownMenuItem(text = { Text("Heading 3") }, onClick = { onCommand(WEditorCommand.ToggleBlockType(TextBlockType.H3)); showTextStyleDropdown = false })
                    DropdownMenuItem(text = { Text("Quote") }, onClick = { onCommand(WEditorCommand.ToggleBlockType(TextBlockType.Quote)); showTextStyleDropdown = false })
                }
            }

            ToolbarDivider(dividerColor)

            // 3. Pen / Drawing
            ToolbarIconButton(
                icon = Icons.Default.Gesture,
                contentDescription = "Drawing",
                isActive = snapshot.isDrawingMode,
                onClick = { onCommand(WEditorCommand.SetDrawingMode(!snapshot.isDrawingMode)) }
            )

            // 3b. Text Box
            ToolbarIconButton(
                icon = Icons.Default.TextFields,
                contentDescription = "Text Box",
                onClick = { onCommand(WEditorCommand.AddTextBox) }
            )

            ToolbarDivider(dividerColor)

            // 4. Font Family
            Box {
                ToolbarTextButton(
                    text = currentFontFamily,
                    onClick = { showFontFamilyDropdown = true }
                )
                DropdownMenu(expanded = showFontFamilyDropdown, onDismissRequest = { showFontFamilyDropdown = false }) {
                    fontFamilies.forEach { family ->
                        DropdownMenuItem(
                            text = { Text(family) },
                            onClick = { onCommand(WEditorCommand.ApplyFont(family)); showFontFamilyDropdown = false }
                        )
                    }
                }
            }

            ToolbarDivider(dividerColor)

            // 5. Font Size
            Box {
                ToolbarTextButton(
                    text = currentFontSize?.toString() ?: "--",
                    onClick = { showFontSizeDropdown = true }
                )
                DropdownMenu(expanded = showFontSizeDropdown, onDismissRequest = { showFontSizeDropdown = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                    fontSizes.forEach { size ->
                        DropdownMenuItem(
                            text = { Text(size.toString()) },
                            onClick = { onCommand(WEditorCommand.SetFontSize(size)); showFontSizeDropdown = false },
                            trailingIcon = { if (currentFontSize == size) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            ToolbarDivider(dividerColor)

            // 6. Inline Formatting
            listOf(
                Triple(Icons.Default.FormatBold, "Bold", SpanType.Bold),
                Triple(Icons.Default.FormatItalic, "Italic", SpanType.Italic),
                Triple(Icons.Default.FormatUnderlined, "Underline", SpanType.Underline),
                Triple(Icons.Default.FormatStrikethrough, "Strikethrough", SpanType.Strikethrough),
                Triple(Icons.Default.FormatColorFill, "Highlight", SpanType.Highlight),
            ).forEach { (icon, label, type) ->
                ToolbarIconButton(
                    icon = icon,
                    contentDescription = label,
                    isActive = type in activeSpanTypes,
                    onClick = { onCommand(WEditorCommand.ApplySpan(type)) }
                )
            }

            ToolbarDivider(dividerColor)

            // 7. Lists
            ToolbarIconButton(
                icon = Icons.Default.FormatListNumbered,
                contentDescription = "Numbered List",
                isActive = currentBlockType == TextBlockType.Numbered,
                onClick = { onCommand(WEditorCommand.ToggleBlockType(TextBlockType.Numbered)) }
            )
            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = "Bulleted List",
                isActive = currentBlockType == TextBlockType.Bullet,
                onClick = { onCommand(WEditorCommand.ToggleBlockType(TextBlockType.Bullet)) }
            )

            ToolbarDivider(dividerColor)

            // 8. Alignment
            Box {
                ToolbarIconButton(
                    icon = when(currentAlignment) {
                        "center" -> Icons.Default.FormatAlignCenter
                        "right" -> Icons.AutoMirrored.Filled.FormatAlignRight
                        "justify" -> Icons.Default.FormatAlignJustify
                        else -> Icons.AutoMirrored.Filled.FormatAlignLeft
                    },
                    contentDescription = "Alignment",
                    onClick = { showAlignmentDropdown = true }
                )
                DropdownMenu(expanded = showAlignmentDropdown, onDismissRequest = { showAlignmentDropdown = false }) {
                    DropdownMenuItem(text = { Text("Align Left") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, null) }, onClick = { onCommand(WEditorCommand.SetAlignment("left")); showAlignmentDropdown = false })
                    DropdownMenuItem(text = { Text("Align Center") }, leadingIcon = { Icon(Icons.Default.FormatAlignCenter, null) }, onClick = { onCommand(WEditorCommand.SetAlignment("center")); showAlignmentDropdown = false })
                    DropdownMenuItem(text = { Text("Align Right") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatAlignRight, null) }, onClick = { onCommand(WEditorCommand.SetAlignment("right")); showAlignmentDropdown = false })
                    DropdownMenuItem(text = { Text("Justify") }, leadingIcon = { Icon(Icons.Default.FormatAlignJustify, null) }, onClick = { onCommand(WEditorCommand.SetAlignment("justify")); showAlignmentDropdown = false })
                }
            }

            ToolbarDivider(dividerColor)

            // 9. Indent
            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.FormatIndentDecrease,
                contentDescription = "Decrease Indent",
                onClick = { onCommand(WEditorCommand.DecreaseIndent) }
            )
            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.FormatIndentIncrease,
                contentDescription = "Increase Indent",
                onClick = { onCommand(WEditorCommand.IncreaseIndent) }
            )

        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = if (isActive) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToolbarTextButton(
    text: String,
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ToolbarDivider(color: Color) {
    VerticalDivider(
        modifier = Modifier
            .height(20.dp)
            .padding(horizontal = 2.dp),
        thickness = 1.dp,
        color = color.copy(alpha = 0.2f)
    )
}
