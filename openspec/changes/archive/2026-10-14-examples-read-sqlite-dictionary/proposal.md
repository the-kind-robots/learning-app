## Why

The `:dictionary-import` alias names `dictionary.import`, a namespace that no longer exists, so nothing can fill the CouchDB `dictionary-db` the example backend reads (#446). The SQLite dictionary the client already uses is built and committed; the backend should read that one.

## Changes

- The examples backend looks words up in `dictionary.sqlite` instead of CouchDB `dictionary-db`.
- **BREAKING (ops)**: `dictionary-db` and everything that fills, secures or routes it is removed: `:dictionary-import` alias, `build.clj` target, Deploy Dictionary workflow, `import.sh`, import service unit, `postinst` and `admin-setup.sh` sections, nginx `/db/dictionary-db/` routes, dev-stand section, ops docs, readme.
- The word-lookup index created at boot goes away with its CouchDB.

## Capabilities

### Modified Capabilities

- `examples`: the dictionary lookup behind a generation request reads the SQLite dictionary; behaviour for known and unknown words is unchanged; a missing dictionary file is specified.
- `deploy-pipeline`: the dictionary reload requirement is removed; the dictionary ships with the app.

## Decisions

- Read the SQLite file already built and served to clients, over restoring an importer.
- The open choices were settled by the owner; see [design.md](design.md#decided).
