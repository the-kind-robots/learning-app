## 1. Clarify the Failure Contract

- [x] 1.1 Document the desired backend/client behavior for unavailable or malformed example generation.

## 2. Tighten Backend and Client Validation

- [x] 2.1 Return clearer non-success responses from `/api/examples` when example generation is unavailable.
- [x] 2.2 Reject malformed success payloads in `fetch-one` before local persistence.
- [x] 2.3 Keep `save-example!` as a final guard without relying on it as the primary error classifier.

## 3. Verify

- [x] 3.1 Add or update tests for invalid example payload handling.
- [x] 3.2 Add or update tests for task retry behavior on invalid example payloads.
- [x] 3.3 Run relevant automated checks and confirm the warning now points to the real failure cause.
