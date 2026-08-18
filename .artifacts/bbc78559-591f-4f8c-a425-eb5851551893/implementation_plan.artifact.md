# Implementation Plan - Unlimited Scrolling & "Write Anywhere"

This plan introduces "Infinite Scrolling" (Scroll Past End) and "Tap to Extend" functionality to the `WEditor`, allowing users to scroll far beyond the current content and start writing at any vertical position below the existing text.

## User Review Required

> [!IMPORTANT]
> The "Write Anywhere" feature is implemented by automatically inserting empty lines when the user taps in the blank space below the document. This maintains the linear block-based structure of the editor while providing a free-form feel.

## Proposed Changes

### [Editor Core]

#### [MODIFY] [WMutationModels.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WMutationModels.kt)
- Add `EXTEND_DOCUMENT` to `WMutationType`.

#### [MODIFY] [WEditorCommand.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WEditorCommand.kt)
- Add `TapBelowDocument(offset: Offset)` command.

#### [MODIFY] [WGestureCoordinator.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WGestureCoordinator.kt)
- Update `onTap` to check `hit.isBelowDocument`.
- If true, emit `WEditorCommand.TapBelowDocument(offset)`.

### [Editor Engine]

#### [MODIFY] [WLayoutProvider.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WLayoutProvider.kt)
- Add `fun getDocumentBottom(): Float` to the interface.

#### [MODIFY] [WLayoutState.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WLayoutState.kt)
- Implement `getDocumentBottom()` by finding the bottom of the last block.

#### [MODIFY] [WMutationEngine.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/logic/WMutationEngine.kt)
- Implement `extendDocument(snapshot: WEditorSnapshot, numLines: Int): WMutationResult`. This will append newlines to the document to create vertical space.

#### [MODIFY] [WEditorHost.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorHost.kt)
- Handle `WEditorCommand.TapBelowDocument`.
- Use `layoutProvider.getDocumentBottom()` to calculate the distance.
- Calculate `numLines` using an estimated line height (e.g., 24dp).
- Apply the `extendDocument` mutation and move the cursor to the new end of the document.

### [Editor UI]

#### [MODIFY] [WEditorRenderer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/engine/renderer/WEditorRenderer.kt)
- Add a `Spacer` at the end of the content `Column` with a large height (e.g., `2000.dp` or viewport height) to enable scrolling past the end of the text.

## Verification Plan

### Manual Verification
- **Unlimited Scrolling**: Open a short note. Verify that you can scroll down significantly even if the content is small.
- **Write Anywhere**: Scroll down to the blank space and tap. Verify that the cursor moves to that position (new lines are added) and you can start typing.
- **Normal Interaction**: Verify that tapping on existing text still works as expected.

### Automated Tests
- Build project to ensure no regressions in the engine's mutation logic.
