# Implementation Plan - Rounded Note Action Menu

The goal is to change the outer shape of the note action menu (the `DropdownMenu` triggered by long-pressing a note) to a more rounded, oblong shape with a large corner radius (approximately 32.dp).

## User Review Required

> [!IMPORTANT]
> The change will be applied to the `DropdownMenu` used for note actions in `HomeScreen` and `NoteWorkspaceScreen`.
> I will also apply it to the overflow menu in `NoteEditorScreen` as it contains the same "action menu" items, ensuring visual consistency across the app for note-related actions.

## Proposed Changes

### Theme

#### [NEW] [Shape.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/theme/Shape.kt)
- Create a new theme file to store the shared menu shape.
- Define `val ActionMenuShape = RoundedCornerShape(32.dp)`.

### Screens

#### [MODIFY] [HomeScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/home/HomeScreen.kt)
- Import `ActionMenuShape`.
- Apply `shape = ActionMenuShape` to the note action `DropdownMenu`.

#### [MODIFY] [NoteWorkspaceScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/NoteWorkspaceScreen.kt)
- Import `ActionMenuShape`.
- Apply `shape = ActionMenuShape` to the note action `DropdownMenu`.

#### [MODIFY] [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- Import `ActionMenuShape`.
- Apply `shape = ActionMenuShape` to the top bar overflow `DropdownMenu`.

## Verification Plan

### Automated Tests
- Build the project to ensure no syntax errors or unresolved references.
- `gradle_build(":app:assembleDebug")`

### Manual Verification
- Long-press a note in `HomeScreen` and verify the menu has large rounded corners.
- Long-press a note in `NoteWorkspaceScreen` and verify the menu has large rounded corners.
- Open a note, tap the overflow menu in the top bar, and verify the menu matches the new rounded style.
- Adjust the `ActionMenuShape` radius in `Shape.kt` and verify it updates all three locations.
