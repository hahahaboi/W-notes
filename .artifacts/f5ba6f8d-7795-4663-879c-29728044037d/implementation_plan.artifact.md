# Implementation Plan - Free Scrolling in Note Editor

The user wants "free scrolling" in the note editor, similar to the home page, especially in empty notes. Currently, the scroll implementation is nested inside the renderer and might be hindered by the gesture layer on top. Moving the scroll to the root of the editor will provide a more consistent and "free" feel.

## Proposed Changes

### [Editor Engine]

#### [MODIFY] [WEditor.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditor.kt)
- Move `rememberScrollState()` to `WEditor`.
- Apply `Modifier.verticalScroll(scrollState)` to the root `Box`.
- Pass `scrollState` down to `WEditorSurface`.
- This ensures the entire editor (content, overlays, and gesture layer) scrolls as a single unit.

#### [MODIFY] [WEditorSurface.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorSurface.kt)
- Update to accept `scrollState` and pass it to `WEditorRenderer`.

#### [MODIFY] [WEditorRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WEditorRenderer.kt)
- Accept `scrollState` as a parameter.
- Remove the local `rememberScrollState()` call.
- Remove `Modifier.verticalScroll(scrollState)` from the internal `Column`.
- Keep the `Spacer` at the bottom to allow scrolling past the content (infinite scrolling feel).
- The existing auto-scrolling logic during drag-and-drop will continue to work using the passed `scrollState`.

## Verification Plan

### Manual Verification
- Deploy the app to an emulator or device.
- Open a note (empty or with content).
- Verify that scrolling works smoothly with one or two fingers.
- Verify that you can scroll past the end of the note content.
- Verify that selection handles and cursor scroll correctly with the content.
- Verify that drag-and-drop auto-scrolling still works (near top/bottom edges).
