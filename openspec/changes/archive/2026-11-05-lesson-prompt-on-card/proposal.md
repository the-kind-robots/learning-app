## Why

On a real lesson the prompt #751 set without a frame at 22 px/600 reads loud, and the panel's answer at 20 px/700 in dark green reads heavy (#765). On a phone the opening keyboard jerks the screen: the browser shortens it, the field and the prompt jump up at their own pace, the progress bar twitches. The main button after an answer stays blue on a green or a red panel.

## Changes

- The prompt is back on its card: a light frame, centred, the instruction under it; its letters lighter than #751's.
- The panel's answer is set like the prompt, in the brand green.
- The prompt stands halfway between the progress bar and the field.
- On a phone the keyboard overlays the lesson, and the lesson lifts its field, button and prompt over it in one 100 ms move; the progress bar stays put.
- «ДАЛЕЕ» and «ЕЩЁ УРОК» take the panel's colour.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: the prompt's card, the lesson under the keyboard, the button's colour — [delta](specs/lesson/spec.md).

## Decisions

- Mockups: https://claude.ai/artifact/HPSwNTWAsspNfwg9UCKwGS (private to the owner), #765.
- The card as before #751, halfway between the progress bar and the field; pinned under the progress bar or a third down looked too high — owner, #765.
- The brand green on the pale-green panel, below 4.5:1: softer reads better here — owner, #765.
- The letters of the prompt and the answer: 17 px, weight 500; the instruction 16 px under the card, clear of its edge — owner, #765.
- The keyboard overlays the lesson, as it does home and words: the lesson then owns the move. The browser's own resize moved the field and the prompt apart; a transition on the prompt alone, 150 ms, lagged behind the field. 100 ms: the shortest move that still reads as one — owner, on the stand, #765.
- On a check the lesson drops in the frame the answer's panel appears, not when the keyboard reports itself gone: waiting showed the panel high for 100 ms, then dropped it — measured frame by frame on the owner's phone, #765.
- The card rides down in 150 ms, ease-out: the frame that draws the panel takes some 50 ms on the phone, and 100 ms read as a jump; the owner asked for a 100 ms ride, #765.
- Desktop keeps its own blank above the prompt.
