## Why

After the last trial a learner who swipes back sees only «ЗАКОНЧИТЬ»; «ЕЩЁ УРОК» is on the final trial alone, so starting another lesson means swiping forward again (#749).

## Changes

- Once the lesson is over, a past trial's panel offers the final trial's pair: «ЕЩЁ УРОК» as its main button and «ЗАКОНЧИТЬ» on the line of its verdict.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: the past trials after the end of a lesson — [delta](specs/lesson/spec.md).

## Decisions

- The pair keeps the final trial's labels and places; the owner's «закрыть» is read as «ЗАКОНЧИТЬ» — #749.
