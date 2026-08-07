# Learning application

German vocabulary learning PWA. Users do spaced-repetition lessons; words come from a
pre-built offline dictionary stored in SQLite/OPFS on the client.

## Project map

| Area                                         | Guide                                                        |
|----------------------------------------------|--------------------------------------------------------------|
| Quick start                                  | this file                                                    |
| Backend (HTTP server, SQLite, API)           | [src/backend/README.md](src/backend/README.md)               |
| Client (CLJS SPA, Replicant/Nexus, OPFS)     | [src/client/README.md](src/client/README.md)                 |
| Dictionary pipeline                          | [tools/dictionary/README.md](tools/dictionary/README.md)     |
| Infrastructure (nginx, systemd, Debian pkg)  | [infra/README.md](infra/README.md)                           |
| Developer docs (philosophy, UI, ops runbook) | [docs/](docs/)                                               |
| Architecture decisions                       | [adr/](adr/)                                                 |

## Development

Quick start (local, http://sprecha.localhost):
1) Follow the "Local Domain Setup (sprecha.localhost)" section in `docs/dev/development-setup.md` (nginx, no certs). Phone testing goes through a Cloudflare tunnel — `docs/dev/mobile-pwa-testing.md`.
2) Run:
```bash
npm install
COUCHDB_URL=http://localhost:5984 COUCHDB_PASS=<pass> clojure -X:dictionary-import
npx shadow-cljs watch app  # terminal 1
clj -M:dev -m core         # terminal 2
```
Open http://sprecha.localhost/ in browser.

Full guide: [docs/dev/development-setup.md](docs/dev/development-setup.md).

## Dictionary

Built files are committed to `resources/dictionary/` and imported from there into CouchDB.

**Rebuild** (when source data changes — see `tools/dictionary/README.md` for stages):
```bash
clojure -T:dictionary build :frequency-file '"tools/dictionary/data/frequency.tsv"'
# optional: enrich RU translations
tools/dictionary/enrich-translations/enrich-translations --input resources/dictionary/dictionary-entries.jsonl
```

**Import** into CouchDB:
```bash
COUCHDB_URL=http://localhost:5984 COUCHDB_PASS=<pass> clojure -X:dictionary-import
```

© 2025. Egor Shundeev, Petr Maslov.
