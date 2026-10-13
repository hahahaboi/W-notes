# Implementation Plan - Final Fixes & Grouping UX Polish

Fix compilation errors related to `drawerProgress` and refactor the grouping UX to directly prompt for a name when "Group" is clicked.

## Proposed Changes

### UI & UX Refinements

#### [MODIFY] [HomeScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/home/HomeScreen.kt)
- Update parameter list to include `drawerProgress: Float`.
- Remove `isGroupingMode` logic from the top bar and dropdown.
- Update "Group" action in the dropdown menu to directly set `showGroupNameDialog = true`.
- Ensure `onOpenDrawer` uses `WAnimatedIcon(progress = drawerProgress)`.

#### [MODIFY] [MultiSelectFloatingActions.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/MultiSelectFloatingActions.kt)
- Remove `isGroupingMode`, `onConfirmGroup`, and `onCancelGroup` parameters and logic.
- The selection bar will remain in standard bulk-action mode during group selection.

### Infrastructure Fixes

#### [MODIFY] [NoteWorkspaceScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/NoteWorkspaceScreen.kt)
- Ensure `drawerProgress: Float` is accepted and passed to `WAnimatedIcon`.

#### [MODIFY] All Note-Related Screens
- Update signatures to take `drawerProgress: Float = 0f` and pass it down to `NoteWorkspaceScreen`.
- Files: `AllNotesScreen.kt`, `StarredNotesScreen.kt`, `TrashNotesScreen.kt`, `UncategorizedNotesScreen.kt`, `LockedNotesScreen.kt`, `FoldersScreen.kt`, `FolderDetailScreen.kt`, `GroupDetailScreen.kt`.

## Verification Plan

### Automated Tests
- Build verification: `./gradlew app:assembleDebug`.

### Manual Verification
1. **Grouping**: Select note -> Menu -> Group -> Enter name -> Success.
2. **Animation**: Open/Close drawer. Verify the menu icon animates correctly on all screens.
