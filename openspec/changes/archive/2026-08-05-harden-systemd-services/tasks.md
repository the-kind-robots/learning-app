# Tasks

- [x] 1. Map each unit's real write paths, network needs, and privilege needs from unit files, admin-setup.sh, backup.sh, import.sh, and backend source
- [x] 2. Hardening block in learning-app-run.service (JVM: MDWE off, /opt/learning-app writable for SQLite)
- [x] 3. Hardening block in learning-app-dictionary-import.service
- [x] 4. Hardening block in learning-app-backup-db.service (WAL source needs -shm/-wal write; borg home)
- [x] 5. Hardening block in learning-app-certbot.service (ProtectSystem=full; caps left broad — root chown)
- [x] 6. Hardening block in learning-app-restart.service (MDWE on; AF_UNIX only)
- [x] 7. `systemd-analyze verify` on all units — caught that inline comments are not parsed by systemd; comments moved to full lines
- [x] 8. Staged server verification documented in the PR (security score before/after, service start, request path, migrations) — cannot run here, no production systemd
