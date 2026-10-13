# Implementation Plan - Pinch to Zoom

Add a smooth, continuous two-finger pinch-to-zoom gesture to the Note Editor. This allows users to zoom in for detailed editing or zoom out for a bird's-eye overview of their notes.

## User Review Required

> [!IMPORTANT]
> - **Scale Range**: Zoom is limited between **50%** (overview) and **200%** (detailed).
> - **No Reflow**: Zooming scales the layout linearly (like a physical page). Text will NOT reflow or wrap differently at different zoom levels.
> - **Fixed UI**: The Top App Bar and the Sticky Formatting Toolbar will NOT zoom. They remain at their standard size and position.
> - **Panning**: When zoomed in, the user can scroll both vertically and horizontally to navigate the enlarged content.

## Proposed Changes

### Core Logic & State
- **[DONE]** Added `zoom: Float` to `WEditorSettings`.
- **[DONE]** Added `WEditorCommand.UpdateZoom`.
- **[MODIFY] [WEditorState.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/state/WEditorState.kt)**: Implement `updateZoom(zoom)` with clamping (0.5 to 2.0).
- **[MODIFY] [WEditorHost.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorHost.kt)**: Bridge the command to state.

### Layout & Rendering
- **[MODIFY] [WLayoutProvider.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WLayoutProvider.kt)**: Add `zoom` property.
- **[MODIFY] [WLayoutState.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WLayoutState.kt)**: Implement `zoom` and adjust hit-testing logic to transform physical viewport coordinates back to unscaled document coordinates.
- **[MODIFY] [WEditorRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WEditorRenderer.kt)**:
    - Wrap the scrollable content in a `Modifier.layout` that scales the measurement result.
    - Add `horizontalScroll` to the main viewport.
    - Scale the `EditorOverlayLayer` (Canvas) and selection handles/carets using `graphicsLayer` to match the content zoom.

### Gestures
- **[MODIFY] [WEditorGestures.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorGestures.kt)**:
    - Update `editorGestures` to detect multi-touch events.
    - Implement `handlePinchZoom` to calculate zoom changes using `calculateZoom()`.
    - Adjust `verticalScrollState` and `horizontalScrollState` during pinch to ensure the zoom anchor (centroid between fingers) remains stable on screen.

## Verification Plan

### Automated Tests
- Build success verification: `gradle_build :app:compileDebugKotlin`.

### Manual Verification
1. **Pinch In (Zoom Out)**: Verify content gets smaller and more note area becomes visible. Verify it stops at 50%.
2. **Pinch Out (Zoom In)**: Verify text gets larger and crisp. Verify it stops at 200%.
3. **Anchor Stability**: Verify that the text under your fingers stays approximately in the same place while pinching.
4. **Scrolling**: Zoom in to 200% and verify you can scroll both vertically and horizontally.
5. **Editing**: Verify you can still place the cursor, select text, and type while zoomed in/out.
6. **Sticky Toolbar**: Confirm the formatting toolbar does NOT zoom and remains pinned to the top as expected.
