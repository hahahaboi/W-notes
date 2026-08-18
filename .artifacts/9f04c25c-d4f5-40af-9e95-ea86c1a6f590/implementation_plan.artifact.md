# Simplify Grouping Flow

This plan outlines the changes required to move the "Group" option to the note long-press menu and simplify the grouping process by using a bottom sheet instead of a multi-select confirmation mode.

## User Review Required

> [!IMPORTANT]
> The current "Grouping Mode" which changes the top bar and provides confirm/cancel buttons at the bottom will be removed. Grouping will now be a direct action, similar to assigning a folder or stapling notes.

## Proposed Changes

### [Data Layer]

#### [MODIFY] [NoteGroupDao.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/data/local/dao/NoteGroupDao.kt)
- Add `addNotesToGroup(groupId: Long, noteIds: List<Long>)` to allow adding notes to an existing group.

#### [MODIFY] [NoteGroupRepository.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/data/repository/NoteGroupRepository.kt)
- Add `addNotesToGroup(groupId: Long, noteIds: List<Long>)` method.

### [ViewModel Layer]

#### [MODIFY] [NoteViewModel.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/viewmodel/NoteViewModel.kt)
- Expose `allGroupsWithNotes` as a `StateFlow`.
- Add `addNotesToGroup(groupId: Long, noteIds: List<Long>)`.
- Update `createGroup` if needed to better handle returning the result or clearing selection (though it might stay as is).

### [UI Components]

#### [MODIFY] [NoteAssignmentSheets.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/NoteAssignmentSheets.kt)
- Add `GroupAssignmentSheet` composable. This will show a list of existing groups and an option to create a new one.

#### [MODIFY] [MultiSelectFloatingActions.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/MultiSelectFloatingActions.kt)
- Remove `isGroupingMode` specific UI elements (Confirm/Cancel buttons).
- Keep the `onGroup` action but it will now trigger the `GroupAssignmentSheet` directly.

### [Screens]

#### [MODIFY] [HomeScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/home/HomeScreen.kt)
- Add "Group" item to the note `DropdownMenu` (long-press menu).
- Remove `isGroupingMode` state and related logic in `Scaffold` (top bar title and actions).
- Update `MultiSelectFloatingActions` usage to handle `onGroup` by showing the `GroupAssignmentSheet`.
- Implement `GroupAssignmentSheet` triggered by both long-press menu and multi-select actions.

---

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.

### Manual Verification
- Long-press a note: "Group" should appear in the menu. Selecting it should show a sheet with existing groups and "New Group" option.
- Select multiple notes: The group icon in the floating action bar should now show the same "Group" sheet instead of entering a confirmation mode.
- Verify that adding to an existing group works.
- Verify that creating a new group from the sheet works.
- Verify that "Confirm Group" and "Cancel Group" (tick/cross) are gone.
