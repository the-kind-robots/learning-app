## 1. Reader

- [x] 1.1 Settle open decisions 1-6 with the owner; adjust the delta specs if an answer differs from the recommendation
- [x] 1.2 Rewrite `src/backend/examples/dictionary.clj` over next.jdbc; drop `ensure-word-lookup-index!` and its call at boot
- [x] 1.3 Add the missing-file startup check
- [x] 1.4 Carry CEFR per decision 6 (dictionary tool build change if option A)
- [x] 1.5 Move `test/backend/examples_test.clj` off the `dictionary-db/` URLs; check lookups against a fixture built by `:dictionary-fixture`
- [x] 1.6 Compare lookups with the old CouchDB answers: done from the deleted importer's history (old keys were `normalize-german` too; see the PR)

## 2. Removal

- [x] 2.1 `deps.edn`: `:dictionary-import` alias; `build.clj`: `dictionary-import` target and its vars
- [x] 2.2 Delete `.github/workflows/deploy-dictionary.yml`
- [x] 2.3 Delete `infra/production/opt/learning-app/dictionary/import.sh` and `learning-app-dictionary-import.service`
- [x] 2.4 `postinst` and `admin-setup.sh`: remove the `dictionary-db` create and `_security` sections
- [x] 2.5 nginx: remove `/db/dictionary-db/` in the production and development configs; revisit the "dictionary reads" timeout comments
- [x] 2.6 `infra/scripts/install-dev-stand.sh`: remove the `dictionary-db` section
- [x] 2.7 CouchDB `10-learning-app.ini` and `learning-app-run.service` comments; `src/backend/push.clj` comment and `push_test.clj` fixture name
- [x] 2.8 `docs/ops/*`, `infra/README.md`, `readme.md`: drop import steps; add the operator note for dropping an old `dictionary-db` (decision 4)

## 3. Close

- [x] 3.1 Run the backend tests; start the backend against the fixture dictionary and request an example
- [x] 3.2 `openspec validate`, archive on the branch
