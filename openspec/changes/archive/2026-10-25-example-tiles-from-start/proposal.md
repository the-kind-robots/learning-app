## Why

Examples are answered with tiles far more often than by typing the whole sentence, yet tiles took a press of the light bulb every time. Typing a tile also asked for umlauts and ß that many keyboards do not have.

## Changes

- An example opens with its tiles; no light bulb.
- Typing places a tile as if its word had no umlauts.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: «An example shown again offers a tile hint» becomes «An example is answered with tiles»; «The light bulb answers the keyboard» and «A long example stays whole while the keyboard is open» go; «Tiles are placed by typing» compares the typed form.

## Decisions

- `<answer-field>` keeps the bulb mode behind one private flag, `bulb?` — the owner wants to switch back with one value, over removing the mode or a new attribute.
- Folding is ä, ö, ü → a, o, u and ß → ss only — what was asked, over also taking ae, oe, ue.
