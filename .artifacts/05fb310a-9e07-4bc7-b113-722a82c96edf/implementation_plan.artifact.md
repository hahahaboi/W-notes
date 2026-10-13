# Implementation Plan - Marker Organization & Visual Hierarchy

Introduce marker types (DEFAULT, IMPORTANT, TODO, REFERENCE), sidebar filtering, and persistent editor indicators.

## User Review Required

> [!IMPORTANT]
> This change includes a database migration (version 10 to 11) to add the `type` column to the `text_markers` table. Existing markers will default to `DEFAULT`.

> [!NOTE]
> I will add a subtle persistent indicator in the editor (a thin dotted underline) for all markers. This will help users identify marked text at a glance.

## Proposed Changes

### Data Layer

#### [MODIFY] [TextMarker.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/data/local/entity/TextMarker.kt)
- Define `MarkerType` enum: `DEFAULT`, `IMPORTANT`, `TODO`, `REFERENCE`.
- Add `type: MarkerType` field to `TextMarker` entity with a default value of `DEFAULT`.

#### [MODIFY] [WNotesDatabase.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/data/local/WNotesDatabase.kt)
- Increment version to 11.
- Add migration logic to add the `type` column if it doesn't exist.

---

### Engine Layer

#### [MODIFY] [WEditorCommand.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WEditorCommand.kt)
- Add `UpdateMarkerType(id, newType)` command.

#### [MODIFY] [WMutationEngine.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WMutationEngine.kt)
- Implement `updateMarkerType(snapshot, id, type)` to update the marker's type in the snapshot.

#### [MODIFY] [WEditorHost.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorHost.kt)
- Dispatch `UpdateMarkerType` command.

---

### UI Layer

#### [MODIFY] [WMarkerSidebar.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WMarkerSidebar.kt)
- **Filtering**: Add a horizontally scrollable row of filter chips at the top of the sidebar.
- **Grouping**: When the "All" filter is active, group markers by type (Important -> Todo -> Reference -> Default).
- **Actions**: Add "Change Type" to the long-press menu, opening a sub-menu or dialog.
- **Visuals**: Use subtle color-coding for different marker types (e.g., Important = Red/Gold, Todo = Blue, Reference = Purple).

#### [MODIFY] [WEditorRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WEditorRenderer.kt)
- In `EditorOverlayLayer`, iterate through all markers and draw a subtle persistent indicator (dotted underline) for each.
- Use colors corresponding to the marker types.

#### [MODIFY] [WMarkerQuickJump.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WMarkerQuickJump.kt)
- Display a small icon or color-coded dot next to each marker in the list to indicate its type.

## Verification Plan

### Automated Tests
- Run `assembleDebug` to verify the build and database schema.

### Manual Verification
1. **Migration**: Open an existing note and verify existing markers are present and have the `DEFAULT` type.
2. **Change Type**: Long-press a marker, select "Change Type", and choose a different category. Verify the visual update in sidebar and editor.
3. **Filtering**: Use the sidebar filter chips to show only "Important" markers.
4. **Editor Indicators**: Verify that marked text has a subtle underline in the editor.
5. **Quick Jump**: Open Quick Jump and verify marker types are displayed.
6. **Mutation Resilience**: Insert text before a categorized marker and verify it stays correctly categorized and anchored.
