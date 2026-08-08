# Deploy preflight verifies unit credentials on the target

## Why

Units shipped by the infra deb may hard-require systemd credentials (`LoadCredentialEncrypted=`): a missing file crash-loops the service with 243/CREDENTIALS after the deploy has already replaced the package. Staging cannot catch this class of failure — its container preseeds every credential by construction — so the gap only surfaces on a real target.

## What changes

- `infra/scripts/list-unit-credentials.sh` extracts the credential names referenced by the shipped units (with a test run by infra-checks).
- `infra/production/opt/learning-app/bin/check-credentials.sh` ships in the deb: run as root via a dedicated sudoers entry, it reports which of the given names are absent from `/etc/credstore.encrypted` — names only, never values.
- `deploy-infra.yml` gains a preflight step before the deb is uploaded: extract the names from the checked-out units, call the checker on the target over ssh, fail the deploy with the list of missing names.
- `DEBIAN/postinst` adds the checker to the deploy user's sudoers line.

## Impact

`.github/workflows/deploy-infra.yml`, `.github/workflows/infra-checks.yml`, `infra/scripts/`, `infra/production/opt/learning-app/bin/`, `infra/production/DEBIAN/postinst`. Spec: deploy-pipeline.
