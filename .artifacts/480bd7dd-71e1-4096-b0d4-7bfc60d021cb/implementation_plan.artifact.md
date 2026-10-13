# Implementation Plan - Improve Rough Button Draggability and Positioning

The goal is to make the floating rough button easier to drag (especially when collapsed) and to ensure that when it is docked (collapsed), it remains fully visible as a "full circle" rather than being partially off-screen.

## User Review Required

> [!NOTE]
> I will increase the touch target area for the collapsed button slightly (to 40dp) while keeping the visual size small (32dp) to make it "more easily draggable" as requested.

## Proposed Changes

### [UI Components]

#### [MODIFY] [FloatingRoughNote.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/FloatingRoughNote.kt)

- **Enable Dragging While Collapsed**: Remove the `if (!isCollapsed)` check in the `onDrag` lambda to allow moving the button in its small state.
- **Adjust Docking Positions**:
    - Change `animatedX` for "left" to `16.dp` (on-screen padding).
    - Change `animatedX` for "right" to `screenWidth - 32.dp - 16.dp` (on-screen padding).
- **Dynamic Size Handling**: Use a `currentSize` variable (32dp for collapsed, 64dp for expanded) to correctly calculate drag boundaries and snap positions.
- **Improve Draggability**:
    - Increase the `Surface` size in collapsed state to `40.dp` for a better touch target, but keep the visual primary-colored circle small (using an inner `Box` or `padding`).
    - Actually, I'll keep the `Surface` at `40.dp` with a transparent background and put a `32.dp` `Box` inside it as the visual button. This provides a 40dp hit area for a 32dp visual.

## Verification Plan

### Manual Verification
- Deploy the app.
- Verify that the collapsed button (the small circle) can be dragged directly without expanding it first.
- Verify that when double-tapping to collapse (dock), it snaps to a position where the full circle is visible near the edge (not cut off).
- Verify that it works consistently on both left and right sides.
- Verify that it's easier to "grab" the small button.
