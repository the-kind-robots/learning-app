## Why

On a phone, the first lesson after a cold start opens with the answer field focused: the keyboard slides up over the screen transition. Later lessons in the same session open without it (#646).

## Changes

- A lesson opened on a touch device leaves the answer field unfocused. Spec: [lesson](specs/lesson/spec.md).
- The answer field's inner textarea loses its `autofocus` attribute. A document honours autofocus once, on insertion, which is why only the first lesson got it. Desktop focus comes from `:effect/mobile-autofocus` (pointer:fine only).
- The attribute also chose the host's `delegatesFocus` target over the light bulb, which came first in the shadow tree. The bulb now follows the field there; it is absolutely positioned, so nothing moves. From the field, Tab reaches the bulb, not Shift+Tab — the owner's choice on #646.

## Capabilities

- Modified: `lesson` — a new requirement, no keyboard on open on a phone.
