# Dev stand host: sprecha.localhost

## Why

`sprecha.local` squats an mDNS-reserved domain, needs an /etc/hosts entry plus mkcert, and phones don't trust the local CA. `*.localhost` is resolved by browsers themselves and is a secure context over plain http — Secure cookies and the service worker work with no certificates at all. The Cloudflare tunnel that serves phones (`<name>.dev.sprecha.de`) was documented but not linked from the dev setup, so the fact was undiscoverable.

## What changes

- Dev Nginx config becomes `sprecha.localhost.conf`: plain http on port 80, no certs, no envsubst; gains the `/api/sync/updates` WebSocket location the prod config already has.
- The tunnel's origin switches to `http://127.0.0.1:80` with `Host: sprecha.localhost` (one dashboard edit) — TLS stays Cloudflare's job.
- Docs: development-setup rewritten around the new host and cross-linked with mobile-pwa-testing both ways; readme quick start updated.
- CDP skill's local target becomes `http://sprecha.localhost`.
- `sprecha.local` is gone everywhere.

## Impact

`infra/development/etc/nginx`, `docs/dev/development-setup.md`, `docs/dev/mobile-pwa-testing.md`, `readme.md`, `.skills/learning-app-cdp`. Spec: repo-browser-debugging.
