## 1. Task Dedupe And Retry Safety

- [x] 1.1 Add unique task creation support with deterministic ids from task type + normalized data and conflict reuse.
- [x] 1.2 Make `example-fetch` tasks unique by word id.
- [x] 1.3 Add retry exhaustion with a 5-attempt cap, dead-letter reason, and latest failure detail.
- [x] 1.4 Add manual reset/retry behavior for failed unique tasks.
- [x] 1.5 Add migration or cleanup for legacy active duplicate example-fetch tasks.
- [x] 1.6 Add tests for same-word duplicate calls, concurrent conflict handling, retry cap, and retry reset.

## 2. Example State Model

- [x] 2.1 Extend example docs with `source` and `modified-at`.
- [x] 2.2 Derive per-word example state: no example, generating, failed, ready.
- [x] 2.3 Pick a random usable example for lesson rendering when multiple exist.
- [x] 2.4 Add tests for state derivation and random usable example selection.
- [x] 2.5 Audit existing AI example docs for missing/outdated structure and regenerate or invalidate unsafe docs.

## 3. Example Management UI

- [ ] 3.1 Show example state and actions on vocabulary rows or word details.
- [ ] 3.2 Add generate and retry actions that use idempotent tasks.
- [ ] 3.3 Add user example create/edit/delete flows.
- [ ] 3.4 Ensure deleting an example does not enqueue automatic generation.
- [ ] 3.5 Add tests for routes/views where practical.

## 4. Lesson Rendering

- [ ] 4.1 Render structured AI examples with existing word popups.
- [ ] 4.2 Render unstructured user examples as plain sentence/translation with no word popups.
- [ ] 4.3 Add lesson tests for random usable example selection and unstructured user rendering.

## 5. Verification And Delivery

- [ ] 5.1 Run OpenSpec validation for `manage-example-generation`.
- [ ] 5.2 Run relevant ClojureScript tests.
- [ ] 5.3 Run browser validation for vocabulary example controls and lesson rendering.
- [ ] 5.4 Archive OpenSpec on the delivery branch before PR merge.
