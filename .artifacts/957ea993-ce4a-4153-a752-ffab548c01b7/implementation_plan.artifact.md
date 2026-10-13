# Implementation Plan — Make All Existing Toolbar Icons Functional

The goal is to connect all placeholder toolbar icons to the editor engine, ensuring correct behavior, persistence, and undo/redo support.

## User Review Required

> [!IMPORTANT]
> - **Numbered Lists**: I will add a new `Numbered` type to `TextBlockType`. Numbering will be handled during rendering or as a block property.
> - **Text Box & Drawing**: I will implement these as new specialized block types in the document model.
> - **AI Assist**: I will add a command placeholder that can be hooked up to an AI service later, as requested.

## Proposed Changes

### Editor Engine & Model

#### [MODIFY] [WBlock.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/model/WBlock.kt)
- Add `Numbered` to `TextBlockType`.
- Add `WDrawingBlock` and `WTextBoxBlock` to the `WBlock` hierarchy.
- Update `WBlockStyle` to include more properties if needed for justification.

#### [MODIFY] [WTextSpan.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/model/WTextSpan.kt)
- Add `FontFamily` to `SpanType`.

#### [MODIFY] [WEditorCommand.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WEditorCommand.kt)
- Add commands: `Undo`, `Redo`, `ApplyFont`, `AddTextBox`, `SetDrawingMode`, `AIAssist`.

#### [MODIFY] [WEditorState.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/state/WEditorState.kt)
- Implement `undo()` and `redo()` by delegating to `WHistoryManager` (which is already present in `WEditorHost`).
- Ensure all mutations from `WMutationEngine` are wrapped in `GeneralMutation` operations for history tracking.

#### [MODIFY] [WMutationEngine.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WMutationEngine.kt)
- **Lists**:
    - Update `toggleBlockType` to handle `Numbered`.
    - Update `splitBlock` (Enter key) to automatically continue numbered/bullet/checklist items.
    - Update `deleteBackward` (Backspace) to exit a list if the item is empty.
- **Indentation**:
    - Update `adjustIndent` to handle nested lists correctly.
- **Formatting**:
    - Implement `applyFont`.
    - Update `setAlignment` to support "justify".
- **Objects**:
    - Implement `addTextBox`.
    - Implement `setDrawingMode`.

---

### UI & Integration

#### [MODIFY] [WEditorHost.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorHost.kt)
- Update `dispatchCommand` to handle the new commands.
- Ensure `Undo`/`Redo` are routed to `inputController` which manages the history.

#### [MODIFY] [WFormattingToolbar.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WFormattingToolbar.kt)
- Connect Numbered List, Bulleted List, and Checklist icons to `ToggleBlockType`.
- Implement functional dropdowns for Font and Font Size.
- Connect Alignment icons (including Justify).
- Connect Indent icons.
- Add missing icons: Undo, Pen, Text Box, Normal Text, Text Container, AI.
- Ensure active states are reflected (e.g., highlighting active alignment or list type).

#### [MODIFY] [WBlockRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WBlockRenderer.kt)
- Add rendering logic for `WDrawingBlock` and `WTextBoxBlock`.
- Handle prefix rendering for `Numbered` blocks (e.g., "1.", "2.").

## Verification Plan

### Automated Tests
- Unit tests for `WMutationEngine` covering list continuation and indentation.
- Unit tests for `WHistory` ensuring new operations are correctly recorded.

### Manual Verification
- Deploy to device/emulator.
- Test every icon as specified in the requirements:
    1. Text Formatting / Paragraph Style.
    2. Numbered List (Enter/Backspace behavior).
    3. Bulleted List (Enter/Backspace behavior).
    4. Text Alignment dropdown.
    5. Increase Indent (nested lists).
    6. Decrease Indent (disabled state).
    7. Text Box (move/resize).
    8. Pen / Drawing.
    9. Checklist (interactivity).
    10. Normal Text activation.
    11. Text Container functional.
    12. Font dropdown.
    13. Font Size dropdown.
    14. Writing Assist / AI button.
    15. Undo button.
- Verify persistence by closing and reopening notes.
