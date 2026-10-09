## Why

The client cannot tell from `/api/examples` whose problem a failure is (#523). A pair the model
cannot answer well, a provider that is down or out of credits, and a provider that throttles all
reach it as a few undifferentiated statuses. The provider's own `401` even arrives as if the
client's session were refused. The provider's `Retry-After` is lost on the way, and the proxy would
replace it with a fixed delay anyway. Two devices sending the same subject — word, glosses and
context — at once pay for two generations. A generation that retries can outlast the proxy's
timeout, so the client gets a `504` for a sentence that was paid for.

## What Changes

- The endpoint's status says whose problem a failure is: the pair's, the provider's, or a provider
  that throttles. `401` is left to the endpoint's own session check.
- The provider's `Retry-After` reaches the client; the proxy adds its fixed delay only to a
  response that carries none.
- Identical subjects in flight at once wait for one generation and get the same result; a request
  whose generation lost the race to store serves the row the cache kept.
- The proxy waits longer than the longest generation.
- The dictionary is read once per request, for the key and the prompt alike; without it nothing
  is generated.
- The cache row's readable glosses are a JSON array, since a gloss may contain a comma.

## Capabilities

### New Capabilities

### Modified Capabilities

- `example-fetch-error-clarity`: adds the status of a failed generation and the timeouts that keep
  the proxy waiting longer than a generation.
- `example-cache`: adds the shared generation for identical subjects in flight and the stored
  winner as the result; renames the key, hit and sharing requirements after the subject, reads the
  dictionary once and generates nothing without it, and stores the readable glosses as a JSON array.
- `production-service-hardening`: the throttled response's `Retry-After` is the proxy's only when
  the application sent none.

## Impact

`src/backend/examples.clj`, `src/backend/examples/provider.clj`, `src/backend/examples/cache.clj`,
`src/backend/examples/dictionary.clj`, the new `src/backend/single_flight.clj`,
`src/backend/core.clj` and their tests; the `/api/examples` locations of both nginx configs. No
change to the client, the data or the dependencies. The client's handling of the new statuses comes
in a separate pull request under the same issue.

How a provider answer is classified follows OpenRouter's published description of the chat
completions endpoint, https://openrouter.ai/openapi.json (`ChatResult`, `ChatChoice`,
`ChatAssistantMessage`, `ChatFinishReasonEnum`, the error responses), and its errors page,
https://openrouter.ai/docs/api/reference/errors-and-debugging (the error object, moderation
metadata, when `Retry-After` is sent, a `200` with empty content). The OpenAPI document does not
describe `error.metadata.reasons` or `Retry-After` for this endpoint; both come from the errors
page.

Requests waiting on a shared generation block their handler thread on the run's `delay`. http-kit
2.8.1's `run-server` defaults to a virtual-thread-per-task executor on JVM 21+, and the package runs
on `openjdk-21-jre-headless`, so a blocked handler holds no pool worker and other requests are still
served. Clojure 1.12's `delay` waits on a `ReentrantLock`, not a monitor, so a waiting virtual thread
does not pin its carrier.
