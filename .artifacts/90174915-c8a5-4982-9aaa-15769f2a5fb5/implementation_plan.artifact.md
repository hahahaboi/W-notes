# Fix: Cursor Tracking and Alignment in Editor

The user reported that the typing cursor "doesn't track the text properly" and appears "above the text instead of beside it." This is caused by a coordinate space mismatch between the gesture handling layer and the block rendering layer, as well as inaccurate tracking of indented text.

## User Review Required

> [!IMPORTANT]
> - **Coordinate Mismatch**: Gestures are currently detected relative to the content area (below the title), but block positions are stored relative to the editor root (including the title). This causes a vertical offset (the "above" issue).
> - **Padding Issue**: Block positions are tracked before indentation is applied, causing the cursor to stay at the left margin instead of following indented text (the "beside" issue).

## Proposed Changes

### Editor Engine

#### [MODIFY] [WEditorGestures.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorGestures.kt)
- Update `editorGestures` to accept `editorCoordinates: LayoutCoordinates?`.
- Use this root coordinate object to convert touch positions to the editor's root coordinate space before passing them to the coordinator.

#### [MODIFY] [WEditorRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WEditorRenderer.kt)
- Pass the `editorCoordinates` (root Box) into the `editorGestures` modifier.
- Refactor how block modifiers are passed to ensure position tracking happens *after* all layout transformations.

#### [MODIFY] [WBlockRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WBlockRenderer.kt)
- Ensure the `onGloballyPositioned` tracking is applied to the actual text content area, not the parent container. This ensures indentation and internal padding are correctly accounted for.

## Verification Plan

### Manual Verification
1. **Vertical Alignment**: Tap any line of text. The cursor should appear exactly where you tapped, not above it.
2. **Horizontal Alignment**: Indent a block (if possible) or type in a block with default padding. The cursor should be exactly beside the last typed character.
3. **Scrolling**: Scroll the document and tap. The cursor should still track correctly.
4. **Title Area**: Verify that tapping near the title doesn't cause weird cursor jumps in the content area.
