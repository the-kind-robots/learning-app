# Tasks

- [x] 1. Deb built byte-for-byte the way infra-checks.yml builds it
- [x] 2. systemd container boots on this docker daemon; incantation recorded in run.sh and README
- [x] 3. Credentials staged via systemd-creds inside the container; OpenRouter gate exercised (postinst refuses to start the app without the key)
- [x] 4. smoke.sh: units, nginx, auth 401, migrations, adoption journal + marker row, backup archive with both stores — PASS/FAIL per line, nonzero exit on failure
- [x] 5. Real run; output recorded in the PR, honest reds included
