# Implementation Plan - Insert Note in Staple Navigation

This plan adds two plus icons below the "Prev" and "Next" buttons in the `NoteEditorScreen`'s bottom bar. These icons allow users to create a new note and insert it exactly before or after the current note within a staple (LinkPath).

## User Review Required

> [!NOTE]
> The new notes will be created with default "Untitled" titles. The user will be navigated to the newly created note immediately.

## Proposed Changes

### [Component] ViewModel

#### [MODIFY] [LinkPathViewModel.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/viewmodel/LinkPathViewModel.kt)
- Add `insertNoteInPath(pathId: Long, noteId: Long, index: Int)` function.
- This function will:
    1. Fetch the current items in the path using `repository.getMembersInPath(pathId)`.
    2. Convert to `HomeItem` list.
    3. Insert the new note at the specified `index`.
    4. Call `repository.updateOrder` to persist the new sequence.

### [Component] UI

#### [MODIFY] [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- Update the `BottomAppBar` layout:
    - Wrap `TextButton("« Prev")` in a `Column`.
    - Add an `IconButton` with `Icons.Default.Add` below "Prev".
    - Wrap `TextButton("Next »")` in a `Column`.
    - Add an `IconButton` with `Icons.Default.Add` below "Next".
- Implement the click logic for these new buttons:
    - Use `viewModel.createNoteAndReturnId("", "")` to create a new empty note.
    - Call `linkPathViewModel.insertNoteInPath(pathId, newId, index)`.
    - Navigate to the new note using `onNavigateToNote(newId, pathId)`.

## Verification Plan

### Manual Verification
1. Open a note that is part of a staple.
2. In the bottom bar, click the "+" button below "Prev".
3. Verify that a new note is created and you are navigated to it.
4. Go back to the "Stapled" screen and verify that the new note is positioned before the previous one.
5. Repeat for the "+" button below "Next".
