## Why

A tile drops the sign of an amount: «1000€» becomes «1000», «50%» becomes «50» (#660). Tiles strip everything at a word's edges that is not a letter or a digit, not only punctuation.

## What Changes

- A tile keeps the symbols of its word: currency, percent and the like stay on it; punctuation still goes. A sign written apart («1000 €», the German norm) joins the tile of the number before it.

What it must do is stated in the delta spec, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `lesson`: «An example is answered with tiles» — a tile keeps its word's symbols.

## Decisions

- Strip punctuation (Unicode `P`) except `%` and `‰`, rather than keep a list of signs to spare: every symbol (`S` — €, $, °, +) stays, and percent, which Unicode files as punctuation, is the only exception needed.
- A sign written apart joins the word before it, rather than standing as a tile of its own — the owner's call: a sign alone is the same detachment as a sign dropped. The tile keeps the space, so the answer reads as the sentence is written.
