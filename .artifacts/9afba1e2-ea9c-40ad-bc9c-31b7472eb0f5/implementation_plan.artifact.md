# Implementation Plan - Fix Multi-Line Selection & Scrolling Sync

The user reports being unable to select words on other lines. This is caused by a coordinate system mismatch between the gesture detection layer (which now scrolls with the document) and the hit-testing engine (which was using viewport-relative coordinates).

## Proposed Changes

### [Component: Editor Renderer]

#### [MODIFY] [WEditorRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WEditorRenderer.kt)
- Introduce `documentCoordinates: LayoutCoordinates?` state to track the physical container of the text blocks.
- Calculate `blockOffsets` relative to this `documentCoordinates` instead of the root `editorCoordinates`.
- This ensures that both the touch offsets from `editorGestures` and the stored block positions are in the same **Document-Local** coordinate space.
- As a side benefit, this significantly improves performance because `onGloballyPositioned` will no longer trigger for every block during scrolling (since they are stationary relative to their direct parent).

### [Component: Rendering Layers]

#### [MODIFY] [WEditorRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WEditorRenderer.kt)
- Update `EditorOverlayLayer` to translate the Canvas based on the scroll offset if needed, or ensure it correctly uses the same coordinate system.
- Since `EditorOverlayLayer` is currently in the viewport root, it needs to account for `scrollState.value` when drawing highlights if the `blockOffsets` are now document-local.

## Verification Plan

### Manual Verification
- **Multi-Line Selection**: Scroll down several pages and select text. Verify the highlight appears exactly where you touched.
- **Title Interaction**: Verify the title remains fully interactive and doesn't conflict with text selection.
- **Scrolling Smoothness**: Drag the scrollbar and verify the document moves smoothly without "jumping" or "jittering" highlights.
- **Selection Handles**: Confirm handles still appear at the correct locations relative to the scrolled text.
