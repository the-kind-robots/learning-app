## Why

The production nginx counts every per-address limit by the socket's peer and hands the same value to the backend as `X-Real-IP` (#801). Behind a CDN the peer is the CDN's edge, so every client arriving through one edge shares one budget: replication connections on `/db/`, the invite throttle in nginx, and the backend's invite requests per day.

## Changes

- nginx takes the client address from `CF-Connecting-IP`, but only for a request whose peer is in the CDN's published ranges. Any other request keeps its socket address, whatever header it carries.
- A timer on the server fetches the published ranges and rewrites the `set_real_ip_from` snippet. nginx is reloaded only when the snippet changed and `nginx -t` passes. A failed fetch, or a list that does not parse, keeps the last good snippet.
- The staging smoke proves both sides: two clients through one listed edge get separate budgets, and a direct request with a forged header keeps its own address.

## Capabilities

### New Capabilities

### Modified Capabilities
- `production-service-hardening`: per-address limits count the client behind the CDN, and only the CDN may name that client.

## Decisions

- The owner chose a server timer over a list baked into the package with a CI check (#801).
- nginx's `realip` module rewrites `$remote_addr` in the post-read phase, before every limit. So the existing zones, the IPv6 `/64` map and `X-Real-IP` follow without change. Rejected: a `map` per zone like the development config's. That would repeat the same rule in every key.
- The generated snippet is not in the package: a reinstall would overwrite it. nginx includes it by a mask, which matches nothing before the first fetch, so `nginx -t` passes without it. No snippet trusts nobody — the behaviour before this change. Rejected: `tmpfiles.d` creating an empty file, one more part for the same result.
- Each fetched line must be a CIDR, or the whole fetch is refused. The snippet is nginx configuration, so text from the network never reaches it unchecked.
- The development config is unchanged: it already reads the header only from loopback, where the tunnel connects.
- The backend is unchanged: it already trusts `X-Real-IP` and listens on loopback only.
