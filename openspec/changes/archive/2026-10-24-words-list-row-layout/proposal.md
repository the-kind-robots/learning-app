## Why

A words-list row is one flex line: the German and the translation share its width, so a phrase and a long translation shrink into two narrow ragged columns (#290). The owner approved a mockup of the row: variant «2» on the design canvas.

## Changes

- The row is rebuilt on the approved mockup: German on the first line, translations on the second, the retention dot at the row's end, no arrow.
- Several translations read on one line, joined by « · » instead of «, ».

## Capabilities

### Modified Capabilities
- `user-phrases`: «Phrases appear in the words list» — the row's layout and the translation separator.

## Decisions

- « · » between translations — a translation may itself hold a comma («смотря как, зависит от обстоятельств»), over «, ».
- Dot at the row's end — it no longer floats beside a tall row's middle line on the left, over a dot before the word.
- No clamp on the translation — the mockup shows it whole, over #290's two-line clamp.
- The type label in the dialog heading (#290) stays out of this change.
