# Implementation Plan: Fix Focus and Dual Cursor Confusion

The user is experiencing confusion because cursors appear in both the title and the notes section simultaneously, and focus doesn't reliably transfer to the notes section on a single tap.

## User Review Required

> [!IMPORTANT]
> This fix involves synchronizing the editor's custom cursor with the actual focus state of the underlying input system. I will also improve focus navigation between the title and the content area.

## Proposed Changes

### Editor State and Commands

#### [MODIFY] [WEditorSubStates.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/state/WEditorSubStates.kt)
- Set default `isVisible` of `WCursorState` to `false` to avoid showing a cursor before the editor is focused.

#### [MODIFY] [WEditorCommand.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WEditorCommand.kt)
- Add `UpdateCursorVisibility(val isVisible: Boolean)` command.

#### [MODIFY] [WEditorState.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/state/WEditorState.kt)
- Implement `updateCursorVisibility(isVisible: Boolean)` to update the snapshot.

#### [MODIFY] [WEditorHost.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorHost.kt)
- Handle the `UpdateCursorVisibility` command in the `onCommand` callback.

### Input Layer and Gestures

#### [MODIFY] [WEditorInputLayer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorInputLayer.kt)
- Add `.fillMaxSize()` to the `BasicTextField` to ensure it covers the entire area and is easily focusable.
- Add `.onFocusChanged` to the `BasicTextField` to trigger `UpdateCursorVisibility` based on focus state.

### Editor Renderer

#### [MODIFY] [WEditorRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WEditorRenderer.kt)
- Add a `FocusRequester` to the Title `TextField`.
- Configure the Title `TextField` with `KeyboardOptions(imeAction = ImeAction.Next)` and `KeyboardActions(onNext = { ... })` to allow easy navigation from Title to Note.

## Verification Plan

### Manual Verification
1.  Open the editor. Verify that no cursor is shown in the note section by default if the title has focus.
2.  Type in the title. Verify only the title's cursor is visible.
3.  Tap the note section once. Verify that:
    - Focus transfers to the note section immediately.
    - The title's cursor disappears.
    - The note's custom cursor appears and starts blinking.
4.  Press "Next" on the keyboard while editing the title. Verify focus moves to the note section.
5.  Verify that typing now goes into the correct section.
