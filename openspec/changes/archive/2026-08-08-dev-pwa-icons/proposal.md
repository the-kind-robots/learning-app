# Proposal: dev-pwa-icons

## Why

Dev and production PWA installs share icons and manifest, so testing both on one device is confusing — the launcher shows two identical apps. GitHub issue: #275.

## What Changes

- Dev icon variants: current icons with a red letter D badge in the corner (`icons/ue-dev-192.png`, `icons/ue-dev-512.png`), committed to the repo.
- `dev-manifest.json` referencing the badged icons (otherwise identical to `manifest.json`).
- HTML shell links the dev manifest when the backend runs from source (`running-from-source?`); packaged production keeps `manifest.json`.
- Service worker precaches the dev assets alongside the existing ones.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `backend-configuration`: the HTML shell SHALL reference the dev manifest with badged icons when running from source, and the production manifest when packaged.

## Impact

- `resources/public/icons/` — two new PNGs.
- `resources/public/dev-manifest.json` — new file.
- `src/backend/core.clj` — manifest link picks by `running-from-source?`.
- `resources/public/js/sw.js` — precache additions.
