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
- Use OpenRouter as the backend transport for example generation.
- Start on a free model path suitable for development and low-volume usage.
- Make prompt behavior easier to tune against the chosen model path.
- Add deterministic quality gates and retries so obviously bad outputs are rejected without relying on a second LLM pass.
- Preserve the current example JSON contract unless we explicitly decide to evolve it.

**Non-Goals**
- Building a full multi-provider abstraction for every AI feature in the app.
- Supporting paid and free routing logic for all future use cases in this task.
- Redesigning the client-side example storage model.

## Decisions

### 1) Switch provider transport to OpenRouter-compatible chat completions

The request shape stays OpenAI-compatible, but the backend target becomes OpenRouter. This keeps the migration small because the current payload already matches the chat-completions style.

Likely backend changes:
- use an OpenRouter base URL
- send the OpenRouter API key from env
- isolate model ID/base URL/header construction so we are not hard-coded to one provider

### 2) Treat free model selection as configuration, not as prompt text

Prompt tuning should not be blocked on transport details. The selected model should come from config, with an initial default aligned to free inference. That gives us room to compare:

- `openrouter/free` as the simplest router path
- one or more pinned `:free` model IDs for reproducibility

Initial implementation choice:
- default to `nvidia/nemotron-3-super-120b-a12b:free`
- keep `EXAMPLE_GENERATION_MODEL` as an override so we can switch back to `openrouter/free` or test other pinned free models without code changes

Comparison used for the initial choice:
- `openrouter/free`: simplest setup, free router path, and OpenRouter documents that it filters free models for features needed by the request, including structured outputs
- `google/gemma-3-27b-it:free`: a reasonable pinned fallback because OpenRouter documents it as free and structured-output capable

Decision:
- ship with `nvidia/nemotron-3-super-120b-a12b:free` as the default because live checks were materially more stable than the router default on the same tricky words
- keep `openrouter/free` as an opt-in override for future comparison when we want broader routing instead of reproducibility
- drop `google/gemma-3-27b-it:free` as the first pinned candidate because current live checks returned `429`

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

- `openrouter/free` is convenient but may route across different free providers, which can make prompt tuning less reproducible.
- Pinning a specific `:free` model is more reproducible but may be less resilient when a specific free provider becomes unavailable.
- Prompt quality improvements may still be model-specific, so we likely need a short bakeoff rather than assuming one prompt will work equally well everywhere.
- Deterministic validation improves reliability, but even with a dictionary-backed target check it still cannot catch every morphology error or sentence-level grammar issue.
- Retry/best-of-N improves acceptance odds, but it increases latency and request volume.

## Recommended Implementation Direction

1. Add OpenRouter transport/config support.
2. Start with a configurable default free model strategy.
3. Rewrite the prompt into a more structured and maintainable form.
4. Add deterministic validation and a small retry budget instead of a critique stage.
5. Run a small manual bakeoff on a representative set of words before locking the default.

## Open Questions

- Should the first default be `openrouter/free` or a pinned free model ID?
- Do we want a separate prompt-evaluation fixture set for representative nouns/verbs/adjectives?
