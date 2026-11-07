## 1. Proxy and timer

- [x] 1.1 Production nginx: `real_ip_header CF-Connecting-IP` plus the generated `set_real_ip_from` snippet in the TLS server
- [x] 1.2 `refresh-cdn-ranges.sh`: fetch the published lists, refuse a line that is not a range, rewrite the snippet, reload only on change after `nginx -t`, keep the last good list on failure
- [x] 1.3 `learning-app-cdn-ranges.service` and `.timer`, sandboxed; nginx includes the snippet by a mask, so it may be absent; postinst enables the timer and starts a first fetch without blocking; postrm removes both

## 2. Staging smoke

- [x] 2.1 The refresh writes a listed edge range; an unchanged list does not reload nginx; a garbled or missing list fails and keeps the last good list
- [x] 2.2 Two clients through one listed edge have separate invite budgets
- [x] 2.3 A direct request forging `CF-Connecting-IP` keeps its socket address

## 3. Verification

- [x] 3.1 `infra/staging/run.sh` green (CI "Staging smoke" on the PR)
- [x] 3.2 The production site under a local nginx: budgets per edge client, forged header ignored
- [x] 3.3 The refresh script against the real published lists and against garbled, missing and empty ones
