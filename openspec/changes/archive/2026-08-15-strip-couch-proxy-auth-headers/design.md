## Context

CouchDB is configured for proxy authentication: nginx's `/db/` location asks the app
(`auth_request /auth/check`), takes the user, roles and token from the subrequest response, and
passes them on as `X-Auth-CouchDB-*`. CouchDB trusts those headers by construction — the entire
boundary is nginx refusing to let a client set them.

Both configs try to enforce that with three `proxy_set_header X-Auth-CouchDB-* ""` directives at
`server` level. nginx's inheritance rule for `proxy_set_header` is all-or-nothing: a `location`
that declares even one `proxy_set_header` of its own inherits none from the enclosing scope. Every
proxying location in both files declares at least `Host` or `Upgrade`, so none of them inherit the
stripping.

## Goals / Non-Goals

**Goals:**

- Every location that proxies upstream strips the three client-supplied headers.
- The gap cannot silently return: a scripted assertion observes what the upstream receives.
- One place to edit when the header set changes.

**Non-Goals:**

- Turning on `proxy_use_secret` / the HMAC token in CouchDB. That is a second layer, tracked
  separately; this change fixes the first one.
- Any change to how `/db/` derives the headers from `auth_request`.
- Touching the running production host. The config is delivered by the infra deb as usual.

## Decisions

**A snippet included per location, not repeated directives.** Three lines copied into five
locations across two files rot independently; the next header added to the set would be added to
some of them. `include` gives one file to edit and one grep to audit.

**The include path is relative (`include snippets/couchdb-proxy-auth-strip.conf;`).** nginx
resolves a relative include against its prefix, which is `/etc/nginx` on the Debian/Ubuntu package
and `/opt/homebrew/etc/nginx` on the macOS dev stand. An absolute `/etc/nginx/...` would have
worked on the server and broken the documented macOS setup.

**`/db/` does not get the snippet.** It already assigns all three headers from its `auth_request`
variables, and an assignment replaces the client's value — that location was never leaky. Adding
the snippet there would declare each header twice, and nginx sends both copies upstream, which is
strictly worse than the current state. The exclusion is spelled out in a comment beside the
assignment so the next reader does not "fix" it.

**The `server`-level directives stay.** They are redundant for every location that includes the
snippet, but they still cover a future location that declares no headers at all. Inheritance is
all-or-nothing, so keeping both cannot produce duplicates.

**The smoke assertion observes the upstream socket, not the response.** Nothing in an upstream
response reveals which headers it received; CouchDB with `require_valid_user = false` answers a
spoofed request exactly as it answers an anonymous one, so a response-based check would pass even
while leaking. The assertion stops the app service, listens on its port with `nc`, sends one
request through nginx carrying `X-Auth-CouchDB-UserName`, and greps the captured request line.
That is the only form of the check that can actually fail when the gap returns.

## Risks / Trade-offs

- **A missing snippet file is a fatal nginx config error, not a warning** → the deb ships it under
  `/etc/nginx/snippets/`, and `docs/dev/development-setup.md` gains the copy step next to the
  existing site-config step. Ordering in the deb is safe: the file is part of the same package as
  the site config that includes it.
- **The smoke assertion stops and restarts `learning-app-run` mid-run** → it runs before the
  assertions that need the app, and restores the unit before continuing; a failure to come back up
  surfaces as the following assertions failing rather than being hidden.
- **`nc` is a new package in the staging image** → `netcat-openbsd`, from the same base repository
  the image already uses; it is test-only and never enters the production deb.
- **The check covers the app upstream, not the CouchDB one** → the dictionary location proxies to
  CouchDB, and stopping CouchDB mid-smoke to listen on 5984 would disturb the later backup
  assertions. The two locations carry the identical include, and the config-level shape is
  verified by nginx itself at reload time.
