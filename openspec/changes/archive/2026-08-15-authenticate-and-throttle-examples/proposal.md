## Why

`GET /api/examples` is the only route in the router that never reads the session cookie
(`src/backend/core.clj:491`), and it is the one route that spends money: every request runs
`examples/generate-one!` into the paid OpenRouter provider (`src/backend/examples/provider.clj`).
`/auth/check`, `/api/sync/updates` and `/api/identity/account` all resolve a token first; this one
answers anybody. At the edge the picture matches: the only `limit_req_zone` is `couchdb`, applied to
`/db/` and `/db/dictionary-db/`, while `/api/examples` falls through to `location /` with no limit at
all. Anyone who knows the URL can bill the owner for as many completions as their connection allows.

## What Changes

- The handler resolves the bearer cookie through `authenticated-user-id`, exactly as `/auth/check`
  does, and answers `401` before any argument is parsed and before the provider is touched. An
  unauthenticated caller cannot even reach the `400` for a missing `word`, because a validation
  message is more than an unauthenticated caller should learn.
- A dedicated `limit_req_zone examples` is added to the production and development nginx configs, with
  a `location /api/examples` that applies it and answers `429`. Sharing the `couchdb` zone would be
  wrong twice over: it is sized for replication bursts at 40r/s, and replication traffic would eat the
  budget of a paid endpoint.
- The `429` carries `Retry-After`, so the throttled client backs off instead of hammering. The client
  already reads that header (`retry-after-ms`, `src/client/adapters/examples.cljs:25`) and reschedules
  the fetch task; without the header it would retry on its own timetable.
- The single client caller, `fetch-one` (`src/client/adapters/examples.cljs:36`), issues a relative
  same-origin request, so the browser already attaches the `auth-token` cookie under the default
  `same-origin` credentials mode. It is made explicit at the call site so the guarantee survives a
  future edit that moves the URL to an absolute origin — the failure mode otherwise is silent: every
  example fetch starts answering 401 and the feature dies quietly.

Out of scope: throttling inside the backend. The edge is where the request budget belongs, and it is
where every other limit in this repo already lives.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `examples`: gains a requirement that generating an example needs an authenticated session — today
  the spec covers only how examples are stored and when fetch tasks are created, and says nothing
  about who may ask for one.
- `production-service-hardening`: gains a requirement that an endpoint which spends money on an
  upstream provider is rate limited at the edge in its own zone.

## Impact

- `src/backend/core.clj` — the `/api/examples` route.
- `infra/production/etc/nginx/sites-available/learning-app.conf` — new zone, new location.
- `infra/development/etc/nginx/sites-available/sprecha.localhost.conf` — the same, so the limit is
  exercised on the dev stand rather than first met in production.
- `src/client/adapters/examples.cljs` — explicit credentials on the one caller.
- `test/backend/core_test.clj` — an end-to-end assertion over the real router: no cookie → 401 and the
  provider is never called; valid cookie → 200.

Note for review: issue #304 edits the same two nginx files (moving the proxy-auth header stripping
into an include). The edits touch different lines, but whichever lands second will want a look at the
merge.
