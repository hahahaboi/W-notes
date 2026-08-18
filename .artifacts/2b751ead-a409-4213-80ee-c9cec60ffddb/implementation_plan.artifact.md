# Implementation Plan - Reposition and Slim Selection Toolbar

The selection toolbar currently appears at a fixed offset from the bottom, which can cause it to be hidden behind the keyboard. Additionally, the user wants the toolbar to have a slimmer design.

## User Review Required

> [!IMPORTANT]
> The toolbar will now use `imePadding()`, meaning it will automatically move up when the keyboard is visible. I will also add a small bottom padding (16dp) for better visual separation.

## Proposed Changes

### Editor UI Component

#### [MODIFY] [WEditorOverlay.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorOverlay.kt)

- **Positioning:** Update the `MultiSelectionToolbar` modifier in `WEditorOverlay` to use `imePadding()` instead of a hardcoded `80.dp` bottom padding.
- **Slim Design:**
    - Reduce `horizontal` padding in `MultiSelectionToolbar` from `16.dp` to `12.dp`.
    - Reduce `vertical` padding from `8.dp` to `4.dp`.
    - Reduce spacing between elements (`spacedBy`) from `12.dp` to `8.dp`.
    - Reduce the corner radius from `24.dp` to `16.dp`.
    - Reduce the height of vertical dividers to match the slimmer design.
    - Change `TextButton` to a more compact version if it feels too bulky, or just rely on the reduced Row padding.

## Verification Plan

### Manual Verification
1. Open the editor.
2. Select some text to trigger the selection toolbar.
3. Verify the toolbar appears at the bottom.
4. Tap on the text to bring up the keyboard.
5. Verify the toolbar moves up and stays visible just above the keyboard.
6. Observe the "slimmer" design and ensure it looks good.
