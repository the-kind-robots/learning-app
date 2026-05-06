# Learning application


## Development

Quick start (local, https://sprecha.local):
1) Follow the "Local Domain Setup (sprecha.local)" section in `docs/dev/development-setup.md` (nginx + mkcert).
2) Run:
```bash
npm install
sqlite3 app.db < initial-setup.sql
COUCHDB_URL=http://localhost:5984 COUCHDB_PASS=<pass> clojure -X:dictionary-import
npx shadow-cljs watch app  # terminal 1
clj -M:dev -m core         # terminal 2
```
Open https://sprecha.local/ in browser.

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
