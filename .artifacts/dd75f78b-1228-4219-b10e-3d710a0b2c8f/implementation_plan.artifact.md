# Implementation Plan - Fix Note Editor Layout and Infinite Loop

Address the empty screen and missing title issues reported by the user.

## User Review Required

> [!IMPORTANT]
> The empty screen was likely caused by an infinite recomposition loop in `NoteEditorScreen` due to state updates during composition, and a fragile layout in `WEditorRenderer` using `IntrinsicSize.Max`.

## Proposed Changes

### UI Components

#### [MODIFY] [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- Fix the `editorHosts` registration logic:
    - Move `editorHosts[tab.id] = host` into a `DisposableEffect` to avoid side effects during composition.
    - Ensure `activeTabId` is stabilized.
- Ensure the `WEditor` call is correctly receiving all parameters.

#### [MODIFY] [WEditorRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WEditorRenderer.kt)
- Revert from `Modifier.layout` based zooming to `graphicsLayer` zooming for better stability.
- Change `Column` width from `IntrinsicSize.Max` to `fillMaxWidth()` to prevent collapsing.
- Remove `defaultMinSize` which depended on early `editorSize` measurements.

## Verification Plan

### Manual Verification
1. Open a note.
2. Verify the title and content are visible immediately.
3. Verify formatting toolbar icons are visible.
4. Verify multi-tab switching works correctly.
5. Verify "Tie Tabs" flow still works (since it relies on these components).
6. Verify no infinite recompositions (UI is responsive).
