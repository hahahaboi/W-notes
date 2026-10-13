# Implementation Plan - Smart Search & Landscape Adaptation

Add a "smart search" feature and make the app layouts adaptive to landscape orientation.

## Proposed Changes

### Smart Search Logic
#### [NEW] [SmartSearchEngine.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/util/SmartSearchEngine.kt)
- Create a utility class to handle smart search logic.
- Implement parsing for:
    - `mention [keyword]` or `note where i mention [keyword]` -> search for `[keyword]`.
    - `[number]` or `[number] words` -> search for notes with word counts near or just over that limit.
    - Standard text search as fallback.

### ViewModel Integration
#### [MODIFY] [NoteViewModel.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/viewmodel/NoteViewModel.kt)
- Update `searchResults` flow to use `SmartSearchEngine`.
- Ensure it filters from `allNotes` reactively.

### Landscape Adaptation
#### [MODIFY] [NoteWorkspaceScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/NoteWorkspaceScreen.kt)
- Replace `GridCells.Fixed(2)` with `GridCells.Adaptive(minSize = 170.dp)` to allow more columns in landscape.
- Update search placeholder to hint at smart search features.
- Update local filtering to use `SmartSearchEngine`.

#### [MODIFY] [HomeScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/home/HomeScreen.kt)
- Replace `GridCells.Fixed(2)` with `GridCells.Adaptive(minSize = 170.dp)`.
- Update search placeholder.

#### [MODIFY] [NoteGrid.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/NoteGrid.kt)
- Replace `GridCells.Fixed(2)` with `GridCells.Adaptive(minSize = 170.dp)`.

## Verification Plan

### Manual Verification
- **Smart Search**:
    - Test "dogs" (keyword).
    - Test "mention dogs" (prompt).
    - Test "note where i mention cats" (full prompt).
    - Test "50" or "50 words" (word count).
- **Landscape**:
    - Rotate device to landscape and verify that the grid shows more columns (e.g., 3 or 4 depending on screen size).
    - Verify that search and other UI elements remain accessible.
