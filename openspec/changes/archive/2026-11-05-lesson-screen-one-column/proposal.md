## Why

The lesson screen is unbalanced: on desktop the main button jumps from the centre to the right edge, the progress bar, the field and the panel each have their own width, the prompt is the quietest text on the screen, and the answer on the panel is smaller than its verdict (#751).

## Changes

- One column on desktop for the progress bar, the prompt, the field, the panel's content and the main button.
- The prompt without a frame, the instruction above it; long words hyphenate.
- The answer panel is a sheet: rounded top on desktop, full width on a phone.
- A tile is never wider than the field.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: the layout of the lesson screen — [delta](specs/lesson/spec.md).

## Decisions

- Agreed layout: canvas boards «E. Принятое», «Длинный пример», «Начертание задания», «Длинное слово» — #751 and its comments.
- A one-word prompt is set like a sentence, not larger — owner, #751.
- The wrong word in the learner's answer is not marked — #752.
- A wrong answer stays red; the mockup's warm yellow was turned down on review — owner, #751.
