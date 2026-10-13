# Implementation Plan - Independent Tab Titles and Selection-based Tab Features

This plan introduces independent tab titles and two new text-selection features: "Create New Tab with Selection" and "Add to Tab". It reuses the existing `WEditor` architecture and ensures stability by avoiding destructive database changes or new relationship systems like TIES.

## User Review Required

> [!IMPORTANT]
> The current persistence logic in `NoteEditorScreen` appears to save tab content as plain text, which might strip spans upon closing and reopening the note. This plan focuses on preserving spans *during the session operations* as requested. If span persistence is missing in the current stable version, this plan maintains that architecture to avoid destructive schema changes.

## Proposed Changes

### [Editor Engine]

#### [MODIFY] [WSelectionManager.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WSelectionManager.kt)
- Add `getSelectedBlocks(state: WSelectionState, blocks: List<WBlock>): List<WBlock>` to extract selection while preserving spans and block structure.
- This will be used by both "Create New Tab with Selection" and "Add to Tab".

#### [MODIFY] [WEditorCommand.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WEditorCommand.kt)
- Add `data class AddToTab(val selectedBlocks: List<WBlock>) : WEditorCommand`.
- Add `data class CreateTabWithSelection(val selectedBlocks: List<WBlock>) : WEditorCommand`.

#### [MODIFY] [WEditorHost.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorHost.kt)
- Update `rememberWEditorHost` to handle `CreateTabWithSelection` and `AddToTab` commands by invoking callbacks provided by the UI layer.

### [UI Components]

#### [MODIFY] [WEditorOverlay.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorOverlay.kt)
- In `MultiSelectionToolbar`, add two new `DropdownItem`s:
    - "Create New Tab with Selection"
    - "Add to Tab"
- These will trigger the new commands with the extracted selected blocks.

#### [MODIFY] [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- **Feature 1 (Independent Titles):**
    - Update `GetOrCreateHost` to initialize `WDocument` metadata with the current `tab.title`.
    - Update `WEditor` call to handle `onTitleChange` by updating the `NoteTab.title` instead of (or in addition to) the `Note.title`.
    - Modify `onAddTab` to create tabs with the title "New Tab" instead of inheritance.
- **Feature 2 (Create New Tab with Selection):**
    - Implement the callback to create a new tab with the extracted blocks.
- **Feature 3 (Add to Tab):**
    - Implement a tab selection dialog/popup using the existing UI style.
    - Implement the logic to append extracted blocks to the selected tab.

### [Data Layer]

#### [MODIFY] [NoteViewModel.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/viewmodel/NoteViewModel.kt)
- Add `appendBlocksToTab(tabId: Long, blocks: List<WBlock>)` to handle the rich text appending logic.
- Ensure `addTabAndReturnId` is used with "New Tab" as default title if none provided.

## Verification Plan

### Automated Tests
- Run Gradle sync and build to ensure no compilation errors.

### Manual Verification
1. **New Tab Title:** Create a new tab and verify it is titled "New Tab" (or similar default) and doesn't copy the previous tab's title.
2. **Title Independence:** Edit a tab title and verify other tabs' labels remain unchanged.
3. **Create New Tab with Selection:**
    - Select text (one word, one paragraph, multiple paragraphs).
    - Use the toolbar menu to create a new tab.
    - Verify the new tab contains the exact selection with formatting.
    - Verify navigation to the new tab.
4. **Add to Tab:**
    - Select text.
    - Use the toolbar menu to "Add to Tab".
    - Select an existing tab (including the current one).
    - Verify content is appended to the end of the destination tab.
    - Verify original content is untouched.
5. **Persistence:**
    - Perform operations, switch tabs, close note, reopen, and restart app.
    - Verify data is correctly saved.
