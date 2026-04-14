## Context

The current example generator calls `https://api.openai.com/v1/chat/completions` directly with a hard-coded model and a large prompt. The backend expects a structured JSON object keyed by input word, and the client later persists a single example document per word.

Two concerns are coupled right now:

1. transport/provider choice
2. prompt quality

A third concern emerged during live validation:

3. post-generation quality control

We need to change all three, but in a way that still lets us diagnose failures cleanly.

## Goals / Non-Goals

**Goals**
- Use a configurable provider path for backend example generation, with Groq as the default transport.
- Start on a low-friction model path suitable for development and low-volume usage.
- Make prompt behavior easier to tune against the chosen model path.
- Add deterministic quality gates and retries so obviously bad outputs are rejected without relying on a second LLM pass.
- Preserve the current example JSON contract unless we explicitly decide to evolve it.

**Non-Goals**
- Building a full multi-provider abstraction for every AI feature in the app.
- Supporting paid and free routing logic for all future use cases in this task.
- Redesigning the client-side example storage model.

## Decisions

### 1) Switch provider transport to Groq-compatible chat completions

The request shape stays OpenAI-compatible, but the backend target becomes Groq. This keeps the migration small because the current payload already matches the chat-completions style.

Likely backend changes:
- use a Groq base URL
- send the Groq API key from env
- isolate model ID/base URL/header construction so we are not hard-coded to one provider

### 2) Treat model selection as configuration, not as prompt text

Prompt tuning should not be blocked on transport details. The selected model should come from config, with an initial default aligned to free inference. That gives us room to compare:

- `openai/gpt-oss-20b` on Groq as the first default
- one or more alternative model IDs for future comparison if quality or quotas require it

Initial implementation choice:
- default to `openai/gpt-oss-20b`
- keep `EXAMPLE_GENERATION_MODEL` as an override so we can test stronger Groq models or move providers again without code changes

Comparison used for the initial choice:
- Groq keeps an OpenAI-compatible API surface and publishes clearer model/rate-limit information than OpenRouter free routing
- `openai/gpt-oss-20b` is the smallest Groq model in this family that still supports JSON object / schema style structured outputs

Decision:
- ship with `openai/gpt-oss-20b` on Groq as the default
- keep provider/model overrides in config so we can compare with stronger Groq models or another provider later
- treat `429` as non-retryable so provider-side quota limits fail fast instead of stretching into route-level `504`s

### 3) Separate prompt structure from transport plumbing

The current prompt should be rewritten for readability and controllability:
- clearer task framing
- explicit output invariants
- shorter and more grouped rules by part of speech / structure
- examples or mini-rules only where they materially improve compliance

The important thing is to make prompt quality testable and reviewable without mixing it with header/base-URL changes.

### 4) Prefer deterministic validation and retry over critique loops

Free-model live checks suggest that a second LLM pass is too expensive and too unreliable for this task. The practical path is:
- strengthen the main generation prompt as much as possible
- apply deterministic validation after generation
- retry generation a small number of times and accept the first candidate that passes validation

The deterministic checks should stay simple and robust:
- schema-valid output only
- `glossMismatch` must be false
- sentence length stays within the expected range
- the target lemma appears in `structure` as a plausible dictionary form
- when a Russian gloss is available, the target lemma should also survive a backend dictionary lookup against `dictionary-db`; this catches article/sense mismatches such as `der Leiter` vs `die Leiter`
- malformed or meta-text payloads are rejected immediately

This still does not solve every morphology problem, especially for cases that need deeper syntactic analysis rather than lemma/gloss agreement. But it avoids spending 2–3x tokens on weak-model self-critique that often fails on the same class of errors.

### 5) Preserve the current JSON contract

The backend should continue returning the same logical structure:
- top-level object keyed by input word
- each value containing `value`, `translation`, `structure`

This keeps the rest of the app stable while we change provider/model/prompt internals.

## Risks / Trade-offs

- Groq free quotas may still be tight enough to require a stronger paid/default plan later.
- A smaller Groq default improves latency and predictability, but it may need a stronger fallback model for harder words.
- Prompt quality improvements may still be model-specific, so we likely need a short bakeoff rather than assuming one prompt will work equally well everywhere.
- Deterministic validation improves reliability, but even with a dictionary-backed target check it still cannot catch every morphology error or sentence-level grammar issue.
- Retry/best-of-N improves acceptance odds, but it increases latency and request volume.

## Recommended Implementation Direction

1. Add Groq transport/config support.
2. Start with a configurable default free model strategy.
3. Rewrite the prompt into a more structured and maintainable form.
4. Add deterministic validation and a small retry budget instead of a critique stage.
5. Run a small manual bakeoff on a representative set of words before locking the default.

## Open Questions

- Should the first default stay on `openai/gpt-oss-20b`, or should we bump straight to `openai/gpt-oss-120b` if quality is not good enough?
- Do we want a separate prompt-evaluation fixture set for representative nouns/verbs/adjectives?
