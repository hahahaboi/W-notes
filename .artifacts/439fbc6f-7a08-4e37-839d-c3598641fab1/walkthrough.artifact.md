# Walkthrough - Smart Search & Landscape Adaptation

I have implemented a new Smart Search engine and updated the app's layouts to be adaptive for landscape orientation.

## Changes Made

### 1. Smart Search Engine
I created `SmartSearchEngine.kt` which parses your search queries for special patterns:
- **Mention Prompt**: You can now search using natural language like `"mention dogs"` or `"note where i mention cats"`. It will extract the keyword and find notes containing it.
- **Word Count Search**: If you type a number (e.g., `"100"`) or a number followed by "words" (e.g., `"100 words"`), it will find notes that have a word count near or just over that limit.
- **Standard Search**: Regular keyword search still works as a fallback.

### 2. Adaptive Layouts
I updated the note grids across the app to use `GridCells.Adaptive`.
- In **Portrait**, it will generally show **2 columns**.
- In **Landscape**, it will automatically scale to **3 or more columns** depending on your screen size, making better use of the wide screen.

### 3. Integrated Components
- `NoteViewModel`: Now uses the `SmartSearchEngine` for the main search results.
- `HomeScreen` & `NoteWorkspaceScreen`: Updated with the new adaptive grid and helpful search placeholders.
- `NoteGrid`: Updated to be adaptive for reusable sections.

## How to use Smart Search
> [!TIP]
> Try these examples in the search bar:
> - `mention vacation`
> - `note where i mention grocery list`
> - `50` (to find notes around 50 words)
> - `200 words` (to find long notes)

## Verification
- Verified that the project builds successfully.
- Implemented logic for word count proximity (notes within 10% below or any amount above the target are shown and sorted by closeness).
- Verified that natural language prompts correctly extract keywords.
