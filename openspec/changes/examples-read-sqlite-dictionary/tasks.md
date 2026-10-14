## 1. Reader

- [ ] 1.1 Settle open decisions 1-6 with the owner; adjust the delta specs if an answer differs from the recommendation
- [ ] 1.2 Rewrite `src/backend/examples/dictionary.clj` over next.jdbc; drop `ensure-word-lookup-index!` and its call at boot
- [ ] 1.3 Add the missing-file startup check
- [ ] 1.4 Carry CEFR per decision 6 (dictionary tool build change if option A)
- [ ] 1.5 Move `test/backend/examples_test.clj` off the `dictionary-db/` URLs; check lookups against a fixture built by `:dictionary-fixture`
- [ ] 1.6 Compare lookups with the old CouchDB answers for a sample of words before the importer goes

## 2. Removal

- [ ] 2.1 `deps.edn`: `:dictionary-import` alias; `build.clj`: `dictionary-import` target and its vars
- [ ] 2.2 Delete `.github/workflows/deploy-dictionary.yml`
- [ ] 2.3 Delete `infra/production/opt/learning-app/dictionary/import.sh` and `learning-app-dictionary-import.service`
- [ ] 2.4 `postinst` and `admin-setup.sh`: remove the `dictionary-db` create and `_security` sections
- [ ] 2.5 nginx: remove `/db/dictionary-db/` in the production and development configs; revisit the "dictionary reads" timeout comments
- [ ] 2.6 `infra/scripts/install-dev-stand.sh`: remove the `dictionary-db` section
- [ ] 2.7 CouchDB `10-learning-app.ini` and `learning-app-run.service` comments; `src/backend/push.clj` comment and `push_test.clj` fixture name
- [ ] 2.8 `docs/ops/*`, `infra/README.md`, `readme.md`: drop import steps; add the operator note for dropping an old `dictionary-db` (decision 4)

## 3. Close

- [ ] 3.1 Run the backend tests; start the backend against the fixture dictionary and request an example
- [ ] 3.2 `openspec validate`, archive on the branch
