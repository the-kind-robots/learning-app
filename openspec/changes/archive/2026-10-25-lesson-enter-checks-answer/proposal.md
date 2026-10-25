## Why

On iOS the Done key of the on-screen keyboard in the lesson answer field breaks the line instead of checking the answer (#659). Plain Enter on a desktop keyboard does the same; only Ctrl+Enter checks.

## Changes

- Enter in the answer field checks the answer, as ПРОВЕРИТЬ does. Spec: [lesson](specs/lesson/spec.md).
- The answer field submits its form on Enter while it shows the textarea, as a single-line input does; long answers still wrap.

## Capabilities

- Modified: `lesson` — a new requirement, Enter checks the answer.

## Decisions

- An answer never holds a line break, so Enter has nothing else to do in the field.
- Alt+Enter keeps the light bulb; Enter while tiles are shown keeps its tile meaning.
