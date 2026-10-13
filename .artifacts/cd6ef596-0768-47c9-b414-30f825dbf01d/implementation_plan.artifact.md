# Continuous Sliding Multi-Note Workspace Plan

Redesign the Stacked Tabs into a gapless, horizontally sliding canvas of connected note panels. Every tab remains visible and dynamically resizes as it moves across the viewport.

## User Review Required

> [!IMPORTANT]
> **Dynamic Layout Engine**: I am implementing a custom layout where the width and position of every tab are continuously calculated from a single `scrollOffset`. Adjacent panels will have their borders physically touching (`A.right == B.left`), and "incoming" panels will dynamically overlap their neighbors to create a seamless sliding effect.

## Proposed Changes

### [UI Components]

#### [MODIFY] [StackedTabsWorkspace.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/StackedTabsWorkspace.kt)
- **Mathematical Rail Geometry**:
    - Define a `BellCurve` width function: `W(d) = MinWidth + (MaxWidth - MinWidth) * exp(-d^2 / span)`.
    - Tab `i`'s width is calculated based on its distance from `scrollOffset`.
- **Connected Borders**:
    - Calculate adjacent positions cumulatively: `X[i] = X[i-1] + (W[i-1] + W[i]) / 2`.
    - Result: A gapless strip of notes.
- **Directional Overlap & Z-Order**:
    - Detect scroll direction.
    - If swiping right, left notes overlap right ones.
    - If swiping left, right notes overlap left ones.
    - The "entering" note panel is always given a higher `zIndex`.
- **Live Interaction**:
    - Every visible panel is a live `WEditor`.
    - Independent vertical scroll positions are preserved (via `WEditorHost.verticalScrollState`).
- **Frictionless Momentum**:
    - Use `exponentialDecay` for flinging to ensure the strip glides naturally and settles without aggressive snapping.
- **Responsive Sizing**:
    - Allow multiple large notes to be visible simultaneously if the screen permits.

### [Logic]

#### [MODIFY] [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- Ensure active tab selection from the bottom bar smoothly scrolls the continuous workspace to the target panel.

## Verification Plan

### Manual Verification
1. **Gapless View**: Open Stack Tabs; verify note borders are perfectly flush.
2. **Continuous Swiping**: Swipe slowly; verify all notes move and resize simultaneously without jumps.
3. **Overlapping Motion**:
    - Swipe right; verify left panel slides OVER the right one.
    - Swipe left; verify right panel slides OVER the left one.
4. **Multiple Large Notes**: Verify at least two substantial editable notes can be seen side-by-side.
5. **Live Workspace**: Edit text in Note B while Note A is visible on the left; verify state preservation.
6. **Independent Scroll**: Scroll Note A vertically; verify Note B does not move.
7. **Momentum**: Perform a fast swipe and observe the natural glide and settle behavior.
