## Why

At night the word tiles of the lesson's example trial all but vanish (#812): a tile is `--color-snow`, the night
ground itself, set off by a black shadow and a black ring the night ground swallows.

## Changes

- A tile is `--color-raised` — white by day as now, the sheet of menus and hints at night — ringed in the ink at
  `--tile-edge-opacity`: 4 % by day as now, 16 % at night. Variant C of the canvas page «Плитки в примере».

## Capabilities

### New Capabilities

### Modified Capabilities

- `color-scheme`: the readability requirement gains the example trial's tiles at night.

## Decisions

- An opacity token beside `--soft-fill-opacity`, over a night-only override in the lesson block: blocks name no
  per-scheme values (ADR-0027).
