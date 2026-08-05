# Learning-app systemd services run sandboxed

## Why

The production units carried only NoNewPrivileges/PrivateTmp/ProtectHome (some not even that). A compromised JVM, borg, or certbot process could write anywhere its user could, load kernel-adjacent interfaces, create namespaces, and use any syscall or address family. systemd ships the sandbox; the units just did not ask for it. GH-190.

## What changes

- All four `[Service]` units under `infra/production/etc/systemd/system/` gain a hardening block: ProtectSystem=strict/full with ReadWritePaths for exactly what each service writes, PrivateDevices, kernel-interface protections, RestrictSUIDSGID, RestrictNamespaces, RestrictRealtime, LockPersonality, RestrictAddressFamilies, CapabilityBoundingSet= where the service needs none, SystemCallArchitectures=native, SystemCallFilter=@system-service.
- MemoryDenyWriteExecute stays off for JVM units (HotSpot JIT) and Python units (libffi closures); it is on only for learning-app-restart (plain systemctl).
- Directives whose survival against the real workload is unverifiable from code alone are left out and listed in the PR, not guessed at.
- Timers and the .path unit have no `[Service]` section; nothing to harden there.

## Impact

New spec: `production-service-hardening`. Files: `learning-app-run.service`, `learning-app-backup-db.service`, `learning-app-certbot.service`, `learning-app-restart.service`, `learning-app-dictionary-import.service`.
