# Implementation Plan: Fix Double Digit Input Bug

The user reports that typing numbers in the notes app results in double digits (e.g., typing '1' produces '11'). This is a common issue when using a hidden `BasicTextField` as an input proxy on Android, especially with numeric keypads that may trigger both IME text commits and hardware key events.

## User Review Required

> [!IMPORTANT]
> The fix involves refining the text extraction logic in the input proxy and specifically handling numeric key events to avoid duplicate processing. This should resolve the issue for both soft and hard keyboards.

## Proposed Changes

### Input Layer Refinement

#### [MODIFY] [WEditorInputLayer.kt](file:///Users/Lahari/AndroidStudioProjects/Wnotes/app/src/main/java/com/tv/wnotes/ui/editor/WEditorInputLayer.kt)
- **Simplify Extraction Logic**: Update `onValueChange` to more reliably extract the inserted text by comparing the new and old strings, avoiding issues with unexpected space padding from some IMEs.
- **Consume Numeric KeyEvents**: Update the `onKeyEvent` handler to explicitly consume (return `true`) for numeric keys (0-9 and NumPad 0-9).
    - For hardware keyboards, we will manually trigger `onCommitText` for these keys.
    - For soft keyboards that might send these as key events, consuming them will prevent `BasicTextField` from processing them as a duplicate to the `commitText` event.

### Mutation Engine (Optional check)
- I will verify if `WMutationEngine.insertText` has any logic that might be duplicating input (unlikely based on previous research).

## Verification Plan

### Manual Verification
1.  Open a note.
2.  Using the soft keyboard's numeric row or numeric pad, type various numbers.
3.  Verify that only a single digit appears for each key press.
4.  (If possible) Connect a physical keyboard and verify numeric input still works correctly.
5.  Verify that regular text input and backspace/enter still work as expected.
