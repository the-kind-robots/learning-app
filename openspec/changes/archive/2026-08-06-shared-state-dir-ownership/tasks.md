# Tasks

- [x] 1. Drop StateDirectory/LogsDirectory from learning-app-backup-db.service; add /var/lib/learning-app to ReadWritePaths with the reasoning recorded in the unit
- [x] 2. Pin StateDirectoryMode=2770 on learning-app-run.service, recorded as the sole owner
- [x] 3. Align tmpfiles.d /var/lib/learning-app to 2770
- [x] 4. systemd-analyze verify on both changed units
