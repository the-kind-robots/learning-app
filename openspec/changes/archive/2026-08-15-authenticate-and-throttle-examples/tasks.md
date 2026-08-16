## 1. Backend

- [x] 1.1 `/api/examples` resolves the cookie through `authenticated-user-id` and answers `401`
      before parsing parameters and before calling `examples/generate-one!`
- [x] 1.2 Confirm the route sees parsed cookies — the router-wide `cookie-interceptor` already
      supplies `:cookies`, so no per-route wiring is needed

## 2. Edge

- [x] 2.1 Production conf: `limit_req_zone $binary_remote_addr zone=examples:10m rate=30r/m` and a
      `location /api/examples` applying it with `burst=10 nodelay`, `limit_req_status 429` and a
      `Retry-After` header
- [x] 2.2 Development conf: the same zone and location, so the limit is exercised on the stand
- [x] 2.3 The new location strips the three `X-Auth-CouchDB-*` client headers itself — a location
      that declares its own `proxy_set_header` stops inheriting the server-level stripping (#304)
- [x] 2.4 `Retry-After` only on the throttled answer: `add_header ... always` fires on every
      response, so a `map $status` empties the value elsewhere and nginx omits the header
- [x] 2.5 Both configs load in nginx

## 3. Client

- [x] 3.1 `fetch-one` declares its credentials mode explicitly instead of relying on the same-origin
      default
- [x] 3.2 Confirm there is no second caller of `/api/examples` anywhere in `src/client` — `fetch-one`
      is the only one, reached from the `example-fetch` task handler
- [x] 3.3 Confirm nothing re-issues the request without credentials — the service worker's fetch
      handler ignores `/api/` entirely (no branch matches, so it never calls `respondWith`), and the
      branches that do fire pass the original `Request` to `fetch`
- [x] 3.4 Start the task runner after the component that writes the auth cookie: both sat in the same
      dependency layer, so a queued example fetch could beat the cookie and be refused

## 4. Verification

- [x] 4.1 Backend test over the real router: no cookie → `401`, and the provider is never called
- [x] 4.2 Backend test: a cookie whose token matches an account → `200` with a generated example
- [x] 4.3 Run the backend suite — nothing runs `test/backend/**` in CI today (#323), so run it by
      hand: 25 tests, 59 assertions, 0 failures
- [x] 4.4 Negative control: with the handler reverted, the same test reports `200` for anonymous
      requests and two provider calls that should never have happened
- [x] 4.5 Throttling proven against a running nginx carrying these exact directives: 11 answered,
      the rest `429` with `Retry-After: 5`, and the upstream counted exactly the answered ones
- [x] 4.6 Successful answers carry no `Retry-After`
- [x] 4.7 Client tests still pass: 182 tests, 433 assertions, 0 failures
- [x] 4.8 `openspec validate authenticate-and-throttle-examples --strict`
