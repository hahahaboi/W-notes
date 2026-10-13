# Implementation Plan - Tab-Linking/Navigation System Refactor

Refactor the tab-linking system to use the Right Side Bar (Utilities Sidebar) as the navigation hub instead of direct jumps, popups, or stacked tab views.

## User Review Required

> [!IMPORTANT]
> This change completely removes the "Jump" popups and the intermediate stacked-tab workspace when clicking linked words. Navigation now happens exclusively through the "Ties" section in the Right Side Bar.

## Proposed Changes

### [Component Name] Editor Engine & State

#### [MODIFY] [WEditorState.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/state/WEditorState.kt)
- Add a way to clear active ties when clicking elsewhere. (Already has `dismissTiePopup`, ensure it clears `activeTieIds`).

#### [MODIFY] [WEditorHost.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorHost.kt)
- In `dispatchCommand` for `ShowTabTieDestinations`:
    - Open the Utilities sidebar (`engineState.toggleMarkerSidebar()` if it's closed).
    - Set the `activeTieIds` in the engine state.
- Update automatic dismissal logic to clear `activeTieIds` when clicking elsewhere in the document.

### [Component Name] Editor UI

#### [MODIFY] [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- In `GetOrCreateHost`'s `onCommand` lambda:
    - Remove the logic that opens `StackedTabsWorkspace` (sets `isStackedWorkspaceOpen = true`) when `ShowTabTieDestinations` is received.
    - In `JumpToTabTie` handling, remove `isStackedWorkspaceOpen = true`.
    - Ensure `JumpToTabTie` handles the switch and scroll-to-position correctly (it already seems to have this logic).

#### [MODIFY] [WEditorOverlay.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorOverlay.kt)
- Remove the rendering of `WTiePopup` and `WTieSelectionPopup`. These are no longer needed as navigation is moved to the sidebar.

#### [MODIFY] [WUtilitiesSidebar.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WUtilitiesSidebar.kt)
- Update `WUtilitiesSidebar` to automatically switch to the `TIES` tab when `snapshot.activeTieIds` becomes non-null.
- In `TiesTabContent`:
    - If `snapshot.activeTieIds` is present, display ONLY those ties (destinations).
    - If `snapshot.activeTieIds` is null, show an empty state or instructions (per requirement: do not show stale tabs).
    - Modify the `onClick` of the items to trigger `WEditorCommand.JumpToTabTie(tie.id)`.
    - Ensure the labels clearly show the target tab name.

## Verification Plan

### Automated Tests
- N/A (UI-heavy changes, will verify manually)

### Manual Verification
1. **Link Click Behavior**:
   - Open a note with linked words.
   - Click a linked word.
   - Verify that NO popup appears and the current tab DOES NOT change.
   - Verify that the Right Side Bar opens (if it was closed) and switches to the "Ties" tab.
   - Verify that the "Ties" tab displays the destination tab(s) for the clicked word.
2. **Navigation**:
   - Click a destination tab entry in the Right Side Bar.
   - Verify that the editor switches to that tab.
   - Verify that the editor scrolls to the exact position of the linked word in the target tab.
   - Verify that a navigation highlight appears briefly at the target position.
3. **Multi-Link Support**:
   - Click a word linked to multiple tabs.
   - Verify all destination tabs appear in the sidebar.
   - Click each one and verify independent navigation to correct positions.
4. **State Clearing**:
   - Click a linked word, then click a non-linked area in the editor.
   - Verify that the "Ties" list in the sidebar is cleared (shows empty state).
5. **No Stacked Workspace**:
   - Click a linked word.
   - Verify that the `StackedTabsWorkspace` (the overview of all tabs) does NOT open.
