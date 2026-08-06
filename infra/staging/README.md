# Staging rung 2: the production shape in a systemd container

ADR-0010's middle rung. `run.sh` builds `infra/production` into the same deb
CI produces, boots a systemd container, installs the deb, delivers the app jar
the runbook way, and runs `smoke.sh` — one PASS/FAIL line per claim that infra
PRs used to ship as manual checklists (#199 stop behavior, #200 sandboxes,
#209 adoption, #211 backup contents).

## Run

```sh
# Prerequisite: the app jar, built as deploy-app.yml builds it
npm ci && npx shadow-cljs release app && clojure -T:build uber

bash infra/staging/run.sh
```

Environment knobs: `LEARNING_APP_STAGING_KEEP=1` leaves the container running
for inspection; `LEARNING_APP_STAGING_CONTAINER` / `LEARNING_APP_STAGING_IMAGE`
rename the artifacts (default prefix `learning-app-staging`). No container
ports are published; every check goes through `docker exec`.

## The systemd-in-docker incantation

Three walls were hit on this daemon (docker 29.x, cgroup v2, WSL2 kernel),
each recorded here so none is ever rediscovered (ADR-0010):

1. **PID 1 boot.** The default private cgroup namespace leaves
   `/sys/fs/cgroup` read-only and systemd dies with "Failed to create
   /init.scope control group". Fix: `--cgroupns=host
   -v /sys/fs/cgroup:/sys/fs/cgroup:rw --tmpfs /run --tmpfs /run/lock`.
2. **Unit sandboxes.** `ProtectSystem`, `PrivateTmp` and the credential mount
   die with "Failed to keep CAP_SYS_ADMIN" (status=217/USER) under docker's
   default caps. Fix: `--cap-add=SYS_ADMIN --security-opt seccomp=unconfined
   --security-opt apparmor=unconfined`.
3. **Encrypted credentials.** The subtle one: everything starts, but
   `/run/credentials/<unit>` is empty and the app dies with
   "LEARNING_APP_DB_AUTH_SECRET is required". Docker leaves every mount
   private and systemd-in-a-container skips its usual "remount / rshared"
   boot step, so the staged credential tmpfs is MS_MOVEd into a mount
   namespace nobody observes — silently. Fix: `mount --make-rshared /` inside
   the container once systemd is up. `--privileged` does NOT fix this one.

With all three in place `--privileged` is not needed anywhere, and
`systemd-creds encrypt --with-key=host` works inside the container
(generating `/var/lib/systemd/credential.secret` on first use), so the
encrypted-credential flow — `systemd-creds` in postinst,
`LoadCredentialEncrypted=` in the units — runs for real, not as a stub.

## What a green run proves

- The deb installs cleanly with its declared dependencies on the OS family it
  targets (Ubuntu noble: production is an Ubuntu Hetzner box, and noble is
  what carries openjdk-21 and CouchDB's apt packages — a pure Debian base
  matches production less, not more).
- postinst's credential gates behave: without an OpenRouter key the app unit
  is not started; with borg passphrase + `BORG_REPO` the backup timer is.
- All units become active under their production sandbox settings.
- nginx terminates TLS and proxies; `/db/` without a cookie is 401 via
  `auth_request`; the app's `/auth/check` refuses without a cookie.
- Migrations run against `LEARNING_APP__DB_PATH`.
- A legacy `/opt/learning-app/app.db` placed before the first service start is
  adopted into `/var/lib/learning-app/db.sqlite` (#209's scenario against the
  real unit sandbox).
- `learning-app-backup-db.service` produces a borg archive carrying both the
  SQLite snapshot and `/var/lib/couchdb`.

## What it deliberately does not prove

- **TLS reality.** The certificate is self-signed; certbot never talks to an
  ACME server (its timer being active is all that is checked).
- **Remote backup.** borg targets a local repository with a plaintext test
  passphrase; ssh transport and the real repository are rung 3's job.
- **Kernel and hardware.** The kernel is the host's (WSL2 here); nothing about
  memory pressure, disk, or multi-day behavior transfers.
- **CouchDB provisioning exactly as production.** The debconf answers are
  preseeded with test values where an operator answers prompts during
  `admin-setup.sh --bootstrap`.

Real TLS, real borg, multi-day observation: rung 3, the mini-PC (#215).

## Known red: adoption (a real catch, not a script bug)

The first full run reported 15 PASS and 3 FAIL, all three on adoption. The
deb's own `tmpfiles.d` line — `f /var/lib/learning-app/db.sqlite 0660 webapp
webapp -`, shipped since GH-64 — pre-creates an empty file at the configured
path during postinst, before the app ever starts. #209's adoption then hits
its no-clobber branch ("target exists"), logs `legacy-database-left-behind`,
and leaves the real data stranded at `/opt/learning-app/app.db`. The same
empty file exists on the production server for the same reason, so this
would have surfaced on the next real deploy as stranded accounts. The fix
belongs to #209's scope; once it lands, this smoke goes green.
