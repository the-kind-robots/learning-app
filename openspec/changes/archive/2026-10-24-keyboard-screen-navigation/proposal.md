## Why

On a computer the home screen starts a lesson from the keyboard, but the words and themes screens open and close only by mouse, and the words screen opens with the cursor nowhere (#615).

## What Changes

- Alt+W on home opens the words screen, Alt+T the themes screen.
- Escape closes the words or themes screen, as its corner ✕ does.
- On a computer the words screen opens with the cursor in its search field.

What each of these must do is stated in the delta spec, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `app-navigation`: the words and themes screens open and close from the keyboard; the words screen focuses its search field.

## Decisions

- Alt+W and Alt+T, chosen by the owner. The key is read by its place (`code`), so the Russian layout works too.
- A keystroke offers no more than the screen does, as Alt+Enter already: both work on home only, where the words button and the themes icon are; Alt+W not with an empty vocabulary, which hides the words button.
- Escape leaves the lesson alone: closing it ends the lesson.
- The search field takes focus only under a fine pointer, by the effect home already uses: on a phone it would raise the keyboard.
