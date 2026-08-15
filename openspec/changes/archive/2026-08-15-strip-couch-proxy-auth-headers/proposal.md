## Why

The nginx configs declare `proxy_set_header X-Auth-CouchDB-*  ""` at `server` level to stop a
client from supplying CouchDB proxy-auth headers itself. nginx inherits `proxy_set_header` into a
`location` only when that location declares none of its own, and every location that proxies
upstream declares its own headers — so the stripping reaches none of them. The directives read as
a guard while guarding nothing, which is worse than no guard at all: a reviewer sees them and stops
looking.

## What Changes

- Move the three stripping directives into an nginx snippet and `include` it in every location that
  proxies upstream, in both the production and the development config.
- `/db/` keeps assigning the three headers from its `auth_request` variables; that assignment is
  itself the strip (the client's value is replaced, never appended), and the snippet must not be
  added there or nginx would send each header twice.
- Add a staging smoke assertion that observes what the upstream actually receives: a request
  carrying `X-Auth-CouchDB-UserName` must arrive at the upstream without it.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `production-service-hardening`: new requirement — client-supplied CouchDB proxy-auth headers are
  stripped in every proxying location, not only where nginx happens to inherit them.
- `staging-environment`: the smoke run also asserts the header boundary at the upstream.

## Impact

- `infra/production/etc/nginx/sites-available/learning-app.conf`
- `infra/production/etc/nginx/snippets/` (new snippet, shipped by the infra deb)
- `infra/development/etc/nginx/sites-available/sprecha.localhost.conf` and its snippet
- `infra/staging/smoke.sh`, `infra/staging/Dockerfile` (the assertion needs a listener to observe
  the upstream side)
- `docs/dev/development-setup.md` (the dev stand now installs a snippet as well)
