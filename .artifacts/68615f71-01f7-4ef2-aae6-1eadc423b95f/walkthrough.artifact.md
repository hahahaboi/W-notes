# Walkthrough - Insert Note in Staple Navigation

I have added the ability to create and insert new notes directly from the navigation bar at the bottom of the note editor.

## Changes

### 1. LinkPathViewModel
- Added a new function [insertNoteInPath](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/viewmodel/LinkPathViewModel.kt) that allows placing a note at a specific index within a staple's sequence. It handles the re-ordering of all items in the staple to accommodate the new insertion.

### 2. NoteEditorScreen UI
- Modified the [BottomAppBar](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt) to include two new "+" icons:
    - One is located below the **« Prev** button.
    - One is located below the **Next »** button.
- These buttons are vertically aligned with their respective navigation buttons for a clean look.

### 3. Insertion Logic
- **Plus below Prev**: Creates a new note and inserts it at the current note's position, effectively pushing the current note to the right.
- **Plus below Next**: Creates a new note and inserts it immediately after the current note.
- Both actions automatically navigate the user to the newly created note so they can start writing immediately.

## Verification Results

- **Build**: Successfully compiled the project.
- **Logical Flow**:
    - Insertion before current note (`index`) works as intended.
    - Insertion after current note (`index + 1`) works as intended.
    - Navigation to the new ID is triggered after a short delay to allow the background re-indexing to settle.

> [!TIP]
> This is extremely useful for adding context between two existing stappled notes without having to go back to the list view and drag-and-drop.
