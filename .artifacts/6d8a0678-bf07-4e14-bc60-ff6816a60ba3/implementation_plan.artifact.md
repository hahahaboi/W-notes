# W Notes — Utilities Sidebar Redesign

This plan outlines the redesign of the note's right-side sidebar, renaming it from "Markers" to "Utilities" and replacing its current marker-type categories with three distinct functional tabs: **Markers**, **Context**, and **Ties**.

## User Review Required

> [!IMPORTANT]
> The "Utilities" sidebar will no longer support filtering markers by type (Default, Important, To-Do, Reference) via chips. Instead, all markers will be shown in the "Markers" tab.
>
> Navigation to annotations (Context) and ties (Ties) will be added to this sidebar.

## Proposed Changes

### UI Components

#### [NEW] [WUtilitiesSidebar.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WUtilitiesSidebar.kt)
Create a new sidebar component that replaces `WMarkerSidebar.kt`.

- **Sidebar Header**: Rename "Markers" to "Utilities".
- **Navigation Tabs**: Implement three tabs: `Markers`, `Context`, and `Ties`.
- **Markers Tab**:
    - Displays all text markers derived from `snapshot.markers`.
    - Reuse the visual style of `MarkerPill`.
    - Tapping an item navigates to the marker in the editor.
- **Context Tab**:
    - Displays all annotations from `snapshot.annotations`.
    - Includes annotations on both body text and titles.
    - Displays target text (`anchorText`) and optionally the content as a preview.
    - Tapping an item navigates to the text and shows the context popup via `WEditorCommand.ShowAnnotation`.
- **Ties Tab**:
    - Displays all ties from `snapshot.ties` (and potentially `allTies` for resolving destinations).
    - Shows the linked text and the target tab name (using `tabLabels`).
    - Tapping an item navigates to the tied text in the current note and shows the tie popup via `WEditorCommand.ShowTabTieDestinations`.

#### [MODIFY] [WEditor.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditor.kt)
Replace the usage of `WMarkerSidebar` with `WUtilitiesSidebar`.

- Pass `tabLabels` and `allTies` to `WUtilitiesSidebar`.
- Update content descriptions and labels where they refer to "Markers sidebar".

#### [DELETE] [WMarkerSidebar.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WMarkerSidebar.kt)
Remove the old sidebar implementation once `WUtilitiesSidebar.kt` is fully functional and integrated.

### Editor Logic

#### [MODIFY] [WEditorCommand.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WEditorCommand.kt)
Ensure all necessary commands exist for navigating to annotations and ties from the sidebar. (Existing commands `ShowAnnotation` and `ShowTabTieDestinations` should suffice).

## Verification Plan

### Automated Tests
- No new automated tests are planned as this is a UI redesign, but I will ensure the project compiles and existing tests pass.

### Manual Verification
1. **Sidebar Title**: Open the right sidebar and verify it is titled "Utilities".
2. **Tabs**: Verify three tabs are present: Markers, Context, Ties.
3. **Markers Tab**:
    - Add a marker; verify it appears in the list.
    - Tap a marker; verify the editor scrolls to it and highlights it.
    - Delete a marker; verify it disappears from the list.
4. **Context Tab**:
    - Add context to a text range; verify it appears in the Context tab.
    - Tap the context item; verify the editor scrolls to it and the context popup appears.
5. **Ties Tab**:
    - Create a tie to another tab; verify it appears in the Ties tab with the correct target tab name.
    - Tap the tie item; verify the editor scrolls to the tied text and the tie popup appears.
6. **Empty States**: Verify each tab shows an appropriate empty state when no items are present.
7. **Persistence**: Close and reopen the sidebar/note and verify the state and items are preserved.
