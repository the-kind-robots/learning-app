## Why

Invites are minted from the command line only (`mint-invite`). The operator should grant them from an email, with no admin UI (#650).

## Changes

- A device without an account asks for an invite with the sha256 of a secret it keeps, and its usage stats. Spec: [identity-provisioning](specs/identity-provisioning/spec.md).
- The operator gets an email with Grant and Decline links; each opens a one-button page whose POST decides.
- A granted request's hash becomes a grant, so the device's secret is redeemed through `/api/identity/provision` as it is.
- The mail goes through a Cloudflare Worker with the `send_email` binding, kept in `infra/cloudflare/invite-mail/` and deployed with `wrangler`.
- An invite granted from a request that expires unused can be asked for anew with the same secret.
- A provision that opens no account gives its invite back.
- `:sync/request-invite` in every interface dictionary, for the menu item #677 builds.

## Capabilities

### Modified Capabilities
- `identity-provisioning`: invites come from a request the operator decides, besides `mint-invite`; a failed provision leaves its invite unspent.

## Decisions

- Mail through a Worker that can reach only verified destinations — over SMTP from the backend (decision comment on #650). The operator's address is a Worker secret, never in the repository.
- The links carry a decision token of their own, stored as its sha256 — over the request hash, which the device knows and could grant itself with.
- The operator pages live under `/api/`, which the service worker never answers with the app shell — over a path of their own, which would mean changing the worker.
- Per-IP limit in the backend, in memory — over nginx `limit_req` alone, whose finest rate is per minute; a request is worth a few a day. nginx still throttles the endpoints per minute and caps the body. IPv6 counts by /64, and a cap of 50 new requests a day across all callers bounds an attacker with many addresses — over no global cap, which leaves the operator's mailbox and the Worker's quota open. A day's budget of 100 mail attempts bounds Worker calls even while failed mails keep giving the 50 back.
- The per-address limit costs one map entry per request and sweeps quiet networks once a minute, with at most 10000 networks — over rebuilding the map per request, which a holder of many addresses turned into a CPU outage of the whole backend.
- The backend listens on loopback only, behind nginx — over trusting `X-Real-IP` from anyone who reaches its port.
- A decision is final for its secret; only an invite that expired unused reopens it — over letting a declined device ask again with the same secret.
- A source checkout without a Worker logs the mail instead of sending it; a packaged app without one answers 503.
