## 1. The snippet

- [x] 1.1 Add `infra/production/etc/nginx/snippets/couchdb-proxy-auth-strip.conf` with the three
      clearing directives and a comment saying why a location must include it
- [x] 1.2 Add the same snippet under `infra/development/etc/nginx/snippets/`

## 2. The configs

- [x] 2.1 Include the snippet in every proxying location of
      `infra/production/etc/nginx/sites-available/learning-app.conf` except `/db/`, and note beside
      `/db/`'s assignment why it is excluded
- [x] 2.2 Do the same in `infra/development/etc/nginx/sites-available/sprecha.localhost.conf`
- [x] 2.3 Document the snippet install step in `docs/dev/development-setup.md`

## 3. The assertion

- [x] 3.1 Install `netcat-openbsd` in `infra/staging/Dockerfile`
- [x] 3.2 Add the upstream-observation assertion to `infra/staging/smoke.sh`: stop the app unit,
      listen on its port, send a request through nginx carrying `X-Auth-CouchDB-UserName`, assert
      the captured request has no `X-Auth-CouchDB-*` line, restart the unit
- [x] 3.3 Add the config-shape assertion: every `location` in `nginx -T` that proxies upstream
      either clears or assigns all three headers

## 4. Verification

- [x] 4.1 Run a throwaway nginx on a spare port with the dev config (ports rewritten, header
      directives untouched) against a header-echoing upstream, and show the leak before the fix and
      its absence after, for every location
- [x] 4.2 Confirm `/db/` sends each header exactly once, with the `auth_request` value
- [x] 4.3 `nginx -t` both configs; leave the shared dev stand as it was found
