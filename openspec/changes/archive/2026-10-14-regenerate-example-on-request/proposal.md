## Why

The word edit dialog (#141) lets a learner ask for a different example. The endpoint answers a subject from the shared cache, so asking again returns the same sentence.

## What Changes

- `POST /api/examples` takes a subject and a list of sentences to avoid, and answers an example that is none of them nor the cached one for that subject.
- The answer is for that learner only: it is never written to the shared cache.
- `GET /api/examples` is unchanged.

## Capabilities

### Modified Capabilities
- `examples`: the regeneration request and its answer.
- `example-cache`: a regenerated example is never stored.

## Impact

- `src/backend/core.clj`, `src/backend/examples.clj`, the backend tests.
