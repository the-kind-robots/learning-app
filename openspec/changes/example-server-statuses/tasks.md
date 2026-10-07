## 1. Provider

- [x] 1.1 Read `Retry-After` under the keyword key the http-kit client gives headers, as delta-seconds or HTTP-date, held to a maximum; unreadable is absent
- [x] 1.2 Parse the HTTP body apart from the completion content: no completion is a provider failure, content that is no example is a rejected candidate
- [x] 1.3 Moderation and model refusals are the pair's; other `401`/`403` are the provider's
- [x] 1.4 Errors a retry cannot change are not retried; otherwise the last attempt decides
- [x] 1.5 An unreadable per-attempt timeout falls back to the default and is logged once
- [x] 1.6 An interrupted attempt sets the interrupt flag again and starts no new attempt

## 2. Endpoint

- [x] 2.1 Status by `examples/outcome`, not by the order of a `cond`
- [x] 2.2 `examples.cache` is storage only: `digest`, `lookup` and `store!` by key; `store!` says whether it wrote the row
- [x] 2.3 A generic `single-flight/run` on `delay`; the key is freed inside the run, before anyone receives its result
- [x] 2.4 `examples/get!` reads the dictionary once, computes the key with `examples/subject-key`, and serves its own example when its write won, the stored row when it lost
- [x] 2.5 `question` becomes `subject` across the backend; the `question_sha256` column keeps its name
- [x] 2.6 The readable `translations` column holds a JSON array; older rows stay as they are
- [x] 2.7 A dictionary that cannot be read fails the request: nothing is generated or stored

## 3. Proxy

- [x] 3.1 Production and development nginx add their own `Retry-After` only to a `429` that has none from upstream
- [x] 3.2 Production and development nginx wait longer than the longest generation on `/api/examples`

## 4. Verification

- [x] 4.1 Backend tests: each status, `Retry-After` end to end and its parsing, body and content classification, refusals, the attempt timeout, interruption, one dictionary read, no generation without the dictionary, single-flight joins observed through its hook, the freed key, the stored winner
- [x] 4.2 Both nginx configs pass `nginx -t`, and a stub upstream shows the upstream `Retry-After` passes once while the proxy's fills in only when absent
- [x] 4.3 `clojure -M:test` green over several runs
- [x] 4.4 Requests waiting on a generation hold no pool worker: twenty held on one generation leave another request answered
