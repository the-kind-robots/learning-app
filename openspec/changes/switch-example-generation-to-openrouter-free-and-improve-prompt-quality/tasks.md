## 1. Track the Migration Scope

- [x] 1.1 Document the provider switch and prompt-improvement goals for example generation.

## 2. Move Example Generation to OpenRouter

- [x] 2.1 Replace direct OpenAI transport details with OpenRouter-compatible configuration.
- [x] 2.2 Introduce a configurable free-model path for example generation.
- [x] 2.3 Keep the backend response contract stable for existing client flows.

## 3. Improve Prompt Quality

- [x] 3.1 Rewrite the example-generation prompt into a clearer, more maintainable structure.
- [x] 3.2 Verify that the prompt still enforces the required output fields and linguistic constraints.
- [x] 3.3 Compare at least a small set of free-model candidates or routing strategies before locking the default.
- [x] 3.4 Add deterministic validation, backend-side target-lemma checks, and retry logic for generated examples.

## 4. Verify

- [x] 4.1 Run relevant automated checks.
- [x] 4.2 Manually validate a representative set of generated examples for structure and usefulness.
