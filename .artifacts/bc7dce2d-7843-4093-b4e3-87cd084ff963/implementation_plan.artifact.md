# Implementation Plan - Custom Menu Icon (Two Slanted Lines)

The user wants to replace the standard 3-line hamburger menu icon with a custom "two slanted lines" icon (`\\`). This icon should be used for the sidebar button and its animation.

## Proposed Changes

### [UI Components]

#### [MODIFY] [WAnimatedIcon.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/WAnimatedIcon.kt)
- Update the normalized points for the `progress = 0` state (the "collapsed" or "button" state).
- Instead of three horizontal lines, define two parallel slanted lines using the 4 available segments.
- Segments 1 and 2 will form the first slanted line.
- Segments 3 and 4 will form the second slanted line.
- This will ensure a smooth animation transition into the 'W' shape at `progress = 1`.

**Points for Two Slanted Lines (`\\`):**
- Line 1 (Seg 1 & 2): From (0.35, 0.35) to (0.45, 0.65)
- Line 2 (Seg 3 & 4): From (0.55, 0.35) to (0.65, 0.65)

## Verification Plan

### Manual Verification
- Deploy the app to an emulator.
- Observe the menu icon in the `TopAppBar` on the Home screen. It should show two slanted lines.
- Open the sidebar. The icon should smoothly animate from the two slanted lines into the 'W' shape.
- Check the large icon inside the sidebar; it should also follow this new animation path.
