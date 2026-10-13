# Implementation Plan - Quick Note Feature (Revised)

Relocate the Quick Note "+" button from the bottom floating toolbar to a floating position adjacent to the note card's vertical contextual menu.

## User Review Required

> [!IMPORTANT]
> The Quick Note "+" button will be rendered as a separate `Popup` anchored to the same container as the vertical `DropdownMenu`. This ensures it appears near the menu and selected note without being part of the existing bottom action toolbar.
>
> We will use a custom positioning logic to try and keep it "beside" the menu, while respecting screen boundaries.

## Proposed Changes

### UI Components

#### [MODIFY] [MultiSelectFloatingActions.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/MultiSelectFloatingActions.kt)
- Revert changes: remove the `onQuickNote` parameter and the added "+" button. This component should return to its original state as a multi-select action bar.

#### [NEW] [ContextualQuickNoteButton.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/ContextualQuickNoteButton.kt)
- Create a new Composable that renders a small floating "+" button.
- It will use a `Popup` to appear near the anchor point (the note card).
- It will handle its own visibility based on whether the contextual menu is open for a single note.

### Integration

#### [MODIFY] [HomeScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/home/HomeScreen.kt)
- Remove the `onQuickNote` call from `MultiSelectFloatingActions`.
- Add `ContextualQuickNoteButton` inside the `Box` containing `SwipeSelectableNoteCard`, positioned next to the `DropdownMenu`.
- Ensure it only appears when `contextMenuNoteId == note.id`.

#### [MODIFY] [NoteWorkspaceScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/NoteWorkspaceScreen.kt)
- Apply similar changes to `NoteWorkspaceScreen` for individual notes and grouped notes.

## Verification Plan

### Automated Tests
- N/A

### Manual Verification
1. **Long-press a note**: Verify the vertical action menu appears.
2. **Floating +**: Verify a *second* "+" button appears near the vertical menu, NOT in the bottom toolbar.
3. **FAB consistency**: Verify the existing large FAB (bottom right) remains unchanged.
4. **Interaction**: Tap the new floating "+". Verify the Quick Note input appears near it.
5. **Persistence**: Enter text and tap outside. Verify the text is appended to the note card.
6. **Cancellation**: Tap outside the input without typing. Verify it dismisses.
7. **Selection change**: Select another note. Verify the Quick Note button and input (if open) dismiss.
