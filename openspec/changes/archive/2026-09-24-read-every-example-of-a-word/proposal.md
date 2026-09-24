## Why

The device reads the examples it holds for one word with a query that carries no limit, and the storage layer then answers with its default page of 25. A word holding more than 25 examples — one per theme it sits in — loses the rest to that read, so the device may fetch an example it already holds. Issue #308.

## What Changes

- Reading the examples of one word returns every example held for it, however many.

## Capabilities

### New Capabilities

### Modified Capabilities
- `examples-schema`: adds a requirement that a word's examples are read in full.

## Impact

- `src/client/adapters/examples.cljs` (`of-word`).
- A unit test above the 25-document boundary.
