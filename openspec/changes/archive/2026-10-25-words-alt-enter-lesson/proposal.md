## Why

The words screen shows «НАЧАТЬ УРОК», but Alt+Enter there does nothing: the keystroke is bound on home only (#655).

## What Changes

- Alt+Enter on the words screen starts the lesson, as its button does.

What it must do is stated in the delta spec, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `home-add-form-focus`: the lesson keystroke works on the words screen too.

## Decisions

- A keystroke offers what the screen offers (as Alt+W and Alt+T): the words screen has the button, so it gets the keystroke; with the word dialog open the button is covered, so the keystroke waits.
