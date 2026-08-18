# Implementation Plan - Stacked Tabs Mode

This plan outlines the implementation of a new "Stacked Tabs" workspace mode for the W Notes editor, allowing users to view and edit multiple tabs side-by-side.

## Proposed Changes

### 1. State Management
- Define `TabWorkspaceMode` enum: `NORMAL`, `STACKED`.
- Add `workspaceMode` state to `NoteViewModel` to manage the current editor mode.
- Add `reorderTabs` method to `NoteViewModel` to persist tab reordering.

### 2. UI - Note Editor Screen
- Modify `NoteEditorScreen.kt`:
    - Observe `workspaceMode` from `NoteViewModel`.
    - Conditionally render the normal `WEditor` or a new `StackedTabWorkspace` component.
    - Pass a flag to `WEditor` to hide the sidebar logic when in stacked mode if necessary, or manage it via the `WEditorHost`.

### 3. UI - Bottom Tab Controls
- Modify `BottomTabRow` in `NoteEditorScreen.kt`:
    - Add an upward-facing arrow button beside the `+` button.
    - Implement a `DropdownMenu` (or a lightweight popup) anchored to the arrow button.
    - Add "Stacked Tabs" and "Normal Tabs" options to the menu.

### 4. UI - Stacked Tab Workspace
- Create `StackedTabWorkspace.kt`:
    - Use `LazyRow` to display tabs side-by-side.
    - Each tab will occupy a large portion of the screen (e.g., 90% width) to remain usable as an editor.
    - Use `key` in `LazyRow` items to maintain `WEditorHost` instances correctly.
    - Each item renders a `WEditor` with its own `WEditorHost`.
    - Implement drag-to-reorder functionality for the tabs.

### 5. Editor Component Adjustments
- Modify `WEditor.kt`:
    - Add an optional parameter `hideSidebar` or similar to completely bypass the sidebar logic and reclaimed its space.
    - Ensure it remains fully interactive when used inside the stacked workspace.

## Detailed Component Changes

### [MODIFY] [WEditorState.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorState.kt)
- Add `enum class TabWorkspaceMode { NORMAL, STACKED }`.

### [MODIFY] [NoteViewModel.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/viewmodel/NoteViewModel.kt)
- Add `_workspaceMode: MutableStateFlow<TabWorkspaceMode>`.
- Add `updateWorkspaceMode(mode: TabWorkspaceMode)`.
- Add `reorderTabs(noteId: Long, fromIndex: Int, toIndex: Int)` to update `orderIndex` in the database.

### [MODIFY] [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- Integrate `workspaceMode`.
- Implement `StackedTabWorkspace` call.
- Update `BottomTabRow` with the mode toggle menu.

### [NEW] [StackedTabWorkspace.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/StackedTabWorkspace.kt)
- Implementation of the horizontally scrollable virtualized editor container.
- Drag-and-drop logic for reordering.

### [MODIFY] [WEditor.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditor.kt)
- Add `forceHideSidebar: Boolean = false` to the `WEditor` composable.
- Adjust the layout to expand the editor content when `forceHideSidebar` is true.

## Verification Plan

### Automated Tests
- Build the project to ensure no regressions in existing tab logic.
- (Optional) Unit test for `reorderTabs` in `NoteViewModel`.

### Manual Verification
- Verify the arrow button appears and opens the menu.
- Enable Stacked Tabs and verify horizontal scrolling.
- Verify each stacked tab is editable and maintains its own state (cursor, selection).
- Verify reordering tabs via long-press and drag.
- Verify the marker sidebar is hidden in stacked mode and reappears in normal mode.
- Verify creating a new tab in stacked mode works as expected.
- Verify switching back to normal mode preserves all data and states.
