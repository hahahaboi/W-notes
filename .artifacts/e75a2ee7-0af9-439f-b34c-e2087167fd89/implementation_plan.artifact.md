# Implementation Plan - Fix Dropdown Dismissal and Selection Preservation

Address the issue where clicking outside the toolbar/dropdown makes the entire selection disappear. This will be achieved by implementing a tap-capture scrim when the dropdown is expanded, ensuring that "tapping elsewhere" only closes the dropdown without clearing the text selection.

## User Review Required

> [!NOTE]
> The fix involves adding a transparent overlay (scrim) that is only active when the selection toolbar's dropdown is open. This overlay will intercept taps to close the dropdown while preventing those taps from reaching the editor surface below.

## Proposed Changes

### Editor UI Component

#### [MODIFY] [WEditorOverlay.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorOverlay.kt)

- **Scrim Implementation:**
    - Inside `MultiSelectionToolbar`, add a `Box` that fills the screen when `isExpanded` is true.
    - Apply `Modifier.pointerInput` with `detectTapGestures` to the scrim to toggle `isExpanded = false`.
    - Place the scrim below the toolbar `Column` in the Z-order so toolbar actions still work.
- **Gesture Consumption:**
    - Ensure the scrim consumes the tap so it doesn't propagate to `WEditorSurface`, which would otherwise clear the selection.

## Verification Plan

### Automated Tests
- Gradle build check.

### Manual Verification
- **Dropdown Dismissal:**
    1. Select text.
    2. Open the dropdown.
    3. Tap anywhere in the editor (outside the toolbar/dropdown).
    4. Verify the dropdown closes but the toolbar and text selection **remain active**.
- **Normal Dismissal:**
    1. Select text.
    2. With dropdown **closed**, tap the editor.
    3. Verify selection is cleared and toolbar disappears (standard behavior).
- **Toolbar Interaction:** Ensure buttons in the toolbar still work while the dropdown is open (if reachable) or that clicking them doesn't trigger the scrim unexpectedly.
