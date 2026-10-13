# Cursor Tracking Fix Tasks

- [x] **Coordinate Normalization**
    - [x] Update `WEditorRenderer.kt` to track block positions relative to the gesture-sensitive content area.
    - [x] Ensure the title and divider height doesn't introduce a vertical offset in the engine.
- [x] **Tracking Accuracy**
    - [x] Update `WBlockRenderer.kt` to apply `onGloballyPositioned` to the actual text content.
    - [x] Fix horizontal alignment for indented text blocks.
- [x] **Verification**
    - [x] Verify vertical alignment on tap.
    - [x] Verify horizontal alignment for indented text.
    - [x] Verify tracking during scrolling.
