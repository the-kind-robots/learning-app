## Why

Two actions in the workflows are three and four majors behind: `actions/upload-artifact` sits at v4.6.2 against v7.0.1, and `actions/download-artifact` at v4.3.0 against v8.0.1. Every other action is current — checkout v7, setup-java v5.7, setup-node v7, cache v6.1 — so these two are the only stale pins, and staying on a Node 20 runtime that the newer runners are moving away from is a slow way to acquire a broken pipeline.

One of the intervening majors is not just a runtime bump. `download-artifact` v8 turns a digest mismatch from a warning into an error. Today a torn download is caught only where a checksum is checked by hand: the deploy verifies the jar's sha256 on the server before the atomic rename, but the staging smoke downloads the same jar and boots it with no integrity check at all. Upgrading closes that gap at the point of download, for every consumer, without any workflow writing its own checksum step.

## What Changes

- `actions/upload-artifact` v4.6.2 → v7.0.1 in `build-jar.yml`, `integration.yml` and `staging-smoke.yml`.
- `actions/download-artifact` v4.3.0 → v8.0.1 in `deploy-app.yml` and `staging-smoke.yml`.
- `deps.edn`: the `:build` alias pins `org.clojure/clojure` at 1.12.4 while `deps.edn`'s own `:deps`, `lib/db` and `tools/dictionary` are all on 1.12.5. Align it — a build running an older Clojure than the code it builds is a difference nobody chose.

The breaking parts of those majors do not reach this repo, which is why the jump can be taken in one step:

- **Node 24 (upload v6, download v7)** — every job runs on `ubuntu-latest`; there are no self-hosted runners to update.
- **Single-artifact path change (download v5)** — applies to downloads *by ID*; both call sites fetch by `name`.
- **Direct uploads (upload v7) and `skip-decompress` (download v8)** — opt-in parameters, neither used; uploads stay zipped and downloads keep unzipping them.
- **ESM (upload v7, download v8)** — internal packaging, transparent to callers.

Out of scope, because they were checked and are already current: every npm dependency, and every Maven and git dependency across `deps.edn`, `shadow-cljs.edn`, `lib/db/deps.edn` and `tools/dictionary/deps.edn`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `deploy-pipeline`: gains a requirement that a corrupted artifact download fails the job that downloads it. Integrity is currently asserted only at the server-side rename, which leaves the staging smoke booting an unverified jar.

## Impact

- `.github/workflows/build-jar.yml` — the jar upload.
- `.github/workflows/deploy-app.yml` — the jar download.
- `.github/workflows/staging-smoke.yml` — both.
- `.github/workflows/integration.yml` — the Playwright trace upload on failure.
- `deps.edn` — the `:build` alias.

No application code changes. The verification that matters is the pipeline running end to end: both artifact consumers are exercised by a pull request that touches these workflow files, since `staging-smoke.yml` treats a change to itself or to `build-jar.yml` as relevant.
