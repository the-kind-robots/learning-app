# CouchDB credentials from the environment

## Why

lib/db hardcodes the dev credentials, and a packaged deployment has no way to override them — any environment whose CouchDB password differs loses every admin-side call: provisioning, reconciliation, the push fan-in. Staging never noticed because its preseed matches the dev value.

## What changes

- `db/conn` reads `LEARNING_APP__COUCHDB_USER` / `LEARNING_APP__COUCHDB_PASSWORD`, falling back to the dev values.
- `-main` refuses to boot a packaged app without the password — silently wrong credentials keep the server up while everything admin-side 401s, which is exactly the failure this closes.
- `learning-app-run.service` loads the `couchdb_admin_password` credential postinst and dictionary-import already use, and exports it into the app's environment.

## Impact

`lib/db/src/db.cljc`, `src/backend/core.clj` (+2 tests), `infra/production/etc/systemd/system/learning-app-run.service`. Spec: backend-configuration.
