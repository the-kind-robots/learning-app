## Why

Example generation is currently tied to direct OpenAI API usage and a single hard-coded model. That makes the feature costly, brittle, and harder to debug or iterate on. We also have a large prompt that encodes many linguistic requirements, but it has not been explicitly tuned against a stable free model path.

We want to:

- move example generation onto Groq with a structured-output-friendly model path
- preserve the existing JSON contract used by the client and persistence layer
- improve prompt quality so generated examples are more grammatical, pedagogically useful, and structurally consistent
- disambiguate generation from the actual word meaning stored in the app, not just the bare German surface form

## What Changes

- Replace direct `api.openai.com` example generation calls with Groq-compatible chat completions.
- Introduce a configurable model strategy, starting with a Groq default and keeping provider/model selection in backend config.
- Rework the example-generation prompt to better constrain output quality and structure.
- Send the intended Russian meaning together with the German word so rare or ambiguous lemmas do not drift to the wrong sense.
- Reject obviously meta or non-Russian garbage payloads before they can be persisted as examples.
- Keep the backend/client example contract stable unless a deliberate schema change is introduced.

## Capabilities

### Modified Capabilities
- `examples`: Example generation transport, model selection, and prompting become configurable around a Groq-backed path rather than a single direct OpenAI model.

## Impact

- Affected code: `src/backend/examples.clj`, related backend config/docs/tests.
- Affected behavior: example generation after adding a word, especially in local development.
- Validation focus: transport compatibility, free-model behavior, prompt quality, and schema consistency.
