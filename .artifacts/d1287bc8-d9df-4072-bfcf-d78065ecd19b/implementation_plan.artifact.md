# Folder Visual Cleanup Plan

Update the physical folder design to use a clean white/off-white theme for both the folder itself and its internal pages, while removing the folder icon from the title in previews.

## User Review Required

> [!NOTE]
> The folder and its internal pages will now be predominantly white. I will use very subtle tonal differences, light gray borders, and soft shadows to maintain depth and visibility against light backgrounds.

## Proposed Changes

### UI Components

#### [MODIFY] [PhysicalFolder.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/PhysicalFolder.kt)
- Change `folderColor` from lavender to white (`0xFFFFFFFF`).
- Update `pageColors` to a sequence of white/off-white tones (`0xFFFFFFFF`, `0xFFF9F9F9`, `0xFFF5F5F5`).
- Add a subtle light gray border (`0xFFEEEEEE`) to the folder flap and internal pages to ensure visibility against the app background.
- Adjust the gradient to be a very subtle transition from off-white to pure white.

#### [MODIFY] [FolderPreviewCard.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/components/FolderPreviewCard.kt)
- Change the folder title `Text` to only display `folder.name`, removing `${folder.icon}`.
- (Implicitly) The note previews inside will now sit on the white folder flap, fulfilling the requirement for white content surfaces.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project compiles.

### Manual Verification
- Verify that the folder cards are now white with subtle depth.
- Confirm that the folder emoji is gone from the `FolderPreviewCard` title.
- Ensure the internal "pages" (visible behind the flap) are also white/off-white.
- Verify that the opening animation and other interactions still work correctly.
