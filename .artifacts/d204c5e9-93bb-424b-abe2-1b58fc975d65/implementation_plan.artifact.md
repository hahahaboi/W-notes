# Implementation Plan - Multiple Tabs UI

Implement a horizontally scrollable bottom tab bar for Note editor navigation.

## User Review Required

> [!IMPORTANT]
> **Layout Priority**: If a Note is opened within a "Path" (e.g., `pathId != null`), there is already a `BottomAppBar` showing "Step X of Y". I propose stacking the Tab Bar *above* the Path navigation bar so both are accessible.

> [!NOTE]
> **Automatic Titles**: If a tab is unnamed, the title will be derived from the first 20 characters of its content. If the content is empty, it will default to "New Tab".

## Proposed Changes

### Editor UI

#### [MODIFY] [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- **Bottom Tab Bar**: Add a `BottomAppBar` (or a custom `Surface` at the bottom of the `Scaffold`'s `padding` area) that contains:
    - A `ScrollableTabRow`-like container (custom implementation for better control).
    - Individual Tab items with:
        - Active/Inactive styling (e.g., pill shape or underline).
        - Display name logic (Custom Title ?? Content Preview ?? "Tab X").
    - A "+" IconButton at the end to add new tabs.
- **Title Logic**: Implement a helper `getEffectiveTabTitle(tab: NoteTab): String`.
- **Navigation**: Tapping a tab updates `activeTabId`.
- **Keyboard Handling**: Use `Modifier.imePadding()` to ensure the tab bar stays above the keyboard if desired, or let the OS handle it. The user specified "The Tab bar must never cover the editor" and "Handle keyboard/IME insets correctly". Usually, the tab bar should be hidden or pushed up when the keyboard is open. I will ensure it doesn't overlap the active text field.

### Logic

#### [MODIFY] [NoteViewModel.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/viewmodel/NoteViewModel.kt)
- (Already has `addTab` from previous step, but I'll ensure it works smoothly with the new UI).

## Verification Plan

### Manual Verification
1. Open a Note.
2. Verify the initial tab is visible at the bottom.
3. Tap the "+" button to add a second tab.
4. Verify the UI switches to the second tab and its content is empty.
5. Type "Hello from Tab 2" in the second tab.
6. Switch back to Tab 1 and verify original content.
7. Switch back to Tab 2 and verify "Tab 2" title is generated (or previewed).
8. Add 10 tabs and verify horizontal scrolling works.
9. Open the keyboard and verify the tab bar behaves correctly (moves up or is accessible).
