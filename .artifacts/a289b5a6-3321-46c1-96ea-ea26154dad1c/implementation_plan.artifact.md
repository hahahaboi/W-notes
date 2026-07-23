# Implementation Plan - App Icon & "Stapled" Refactor

This plan outlines the steps to set the provided image as the application icon and rename the "Link Paths" feature to "Stapled" with a paper clip emoji (📎), as part of the overall design polish.

## Proposed Changes

### 1. App Icon Update

Set the provided image as the application icon across all densities and formats.

#### [MODIFY] Icon Resources (`app/src/main/res/mipmap-*`)
- Generate and replace `ic_launcher.webp` and `ic_launcher_round.webp` for all density buckets (mdpi to xxxhdpi) using resized versions of the provided image.

#### [MODIFY] [ic_launcher_background.xml](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/res/drawable/ic_launcher_background.xml)
- Update the background color to match the beige tone of the new icon (`#F5F2E9`).

---

### 2. Rename "Link Paths" to "Stapled"

Refactor the naming and iconography for the "Link Paths" feature.

#### [MODIFY] [LinkPathViewModel.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/viewmodel/LinkPathViewModel.kt) & [LinkPathRepository.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/data/repository/LinkPathRepository.kt)
- Rename functions: `createPath` -> `createStaple`, `assignNoteToPath` -> `stapleNote`, etc.

#### [MODIFY] [MainActivity.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/MainActivity.kt)
- Update Drawer: "Link Paths" -> "Stapled", 🔗 -> 📎.

#### [MODIFY] [HomeScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/home/HomeScreen.kt) & [NoteEditorScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/NoteEditorScreen.kt)
- Update menus and badges: "Add to Link Path" -> "Staple Note", 🔗 -> 📎.

#### [MODIFY] [LinkPathsScreen.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/paths/LinkPathsScreen.kt)
- Rename titles and headers to "Stapled".

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure compilation and resource integrity.

### Manual Verification
- Verify the new icon appears on the device home screen.
- Verify the Navigation Drawer and Note menus show "Stapled" with the 📎 emoji.
