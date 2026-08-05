# Atomic artifact swap on deploy

## Why

The app deploy already writes aside and renames: scp-action lands `learning-app.jar.tmp` in `/opt/learning-app/`, then an ssh step `mv -f`s it over `learning-app.jar` — rename(2) on the same filesystem — and `learning-app-restart.path` (PathChanged on the live jar) restarts the service. Two torn-jar paths remain. The temp name is fixed and shared, so a second concurrent `workflow_dispatch` run uploads into the same `learning-app.jar.tmp` while the first run's `mv` grabs it half-written — the rename is atomic, but it atomically installs a torn jar and triggers the restart watch. And nothing checks the upload survived the tar-over-ssh transfer intact before the swap: `test -f` passes on a truncated file.

## What changes

- The upload path becomes per-run unique: `learning-app.jar.${{ github.run_id }}.tmp`, still inside `/opt/learning-app` so the rename never crosses a filesystem.
- The build records the jar's sha256; the server-side step verifies it against the uploaded temp file and aborts before the swap on any mismatch or missing file (subsumes `test -f`).
- After a successful swap, per-run temp files older than an hour are deleted, so aborted deploys do not accumulate uber jars in `/opt/learning-app`.

## Impact

Added capability: `deploy-pipeline`. Files: `.github/workflows/deploy-app.yml`. Server infra untouched; the restart mechanism (`learning-app-restart.path`) is unchanged. Proof of the full path is the next real deploy.
