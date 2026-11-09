## Why

The statistics' shared picture is white with grey text whatever scheme the app shows (#809): its colours are
literals of the light tokens, missed by the night theme (#413, #805).

## Changes

- The picture names the colour tokens instead of their light values; the sharing adapter already resolves a colour
  as the page computes it, so the picture takes the scheme the app shows. By day it is the same picture.
- The bars take `--soft-fill-opacity`, as the statistics screen's do (half by day, 0.8 at night); the adapter
  resolves an opacity as it resolves a colour.

## Capabilities

### New Capabilities

### Modified Capabilities

- `statistics`: the shared picture is drawn in the colours of the scheme the app shows.

## Decisions

- Tokens resolved by the page, over a second set of literals for the night: one list of colours (ADR-0027).
