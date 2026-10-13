# Implementation Plan - Phone Optimization & UI Bug Fixes

This plan addresses several UX and layout issues that affect the app when running on a phone, specifically focusing on touch targets, responsiveness, and gesture behavior.

## User Review Required

> [!IMPORTANT]
> - **Touch Targets**: I am increasing the width of the `FocusTimerSidebar` handle to 32dp (from 12dp) and adding an invisible padding area to reach the recommended 48dp touch target width.
> - **Gesture Changes**: Swiping on note cards will no longer require a long press. It will be a standard responsive swipe gesture.

## Proposed Changes

### 1. Component Optimization
#### [MODIFY] [FocusTimerSidebar.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/FocusTimerSidebar.kt)
- Increase the handle width to make it easier to tap on phone screens.
- Add an invisible touch expansion area to ensure it meets the 48dp accessibility guideline.

#### [MODIFY] [FloatingRoughNote.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/FloatingRoughNote.kt)
- Dynamically calculate the initial position based on screen dimensions instead of hardcoded values.
- Improve the visual design of the "collapsed" state so it's clearly identifiable as a feature and not a layout glitch.

#### [MODIFY] [SwipeSelectableNoteCard.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/SwipeSelectableNoteCard.kt)
- Replace `detectDragGesturesAfterLongPress` with `detectHorizontalDragGestures`. This removes the delay before swiping, making the app feel significantly more responsive on mobile.

#### [MODIFY] [NoteWorkspaceScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/NoteWorkspaceScreen.kt)
- Simplify the search placeholder text to prevent truncation or wrapping on narrow screens.
- Adjust `GridCells.Adaptive` minimum size if necessary for better phone layouts.

### 2. Layout & Insets
### 3. Editor Engine Stability
#### [MODIFY] [WHitTestEngine.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WHitTestEngine.kt)
- Add vertical tolerance to `findBlockAtY`. Currently, a 1-pixel gap between blocks can cause hit-testing to fail, which is common on high-density phone screens.
- Improve `clampToNearestBlock` to be more robust when touching the very top or bottom of the editor.

#### [MODIFY] [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- Ensure the editor content handles IME (keyboard) insets correctly so the text isn't obscured when typing.
- Add `Modifier.imePadding()` to the main editor container.

#### [MODIFY] [MainActivity.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/MainActivity.kt)
- Ensure the status bar icons adapt to the theme (light/dark) for better legibility on phone.

## Verification Plan

### Manual Verification
- **Touch Targets**: Verify the Focus Timer handle is easy to open with a finger.
- **Gestures**: Verify swiping on note cards is instant and smooth.
- **Keyboard**: Open the editor, type a long note, and verify the screen remains usable when the keyboard is up.
- **Rough Note**: Drag the rough note and verify it doesn't jump to weird positions on first launch.
