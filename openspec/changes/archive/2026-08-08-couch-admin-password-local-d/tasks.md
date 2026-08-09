# Tasks

- [x] 1. postinst: replace the HTTP admin PUT with a drift check (curl -u against `_node/_local/_config/admins`) and, on mismatch, a local.d ini write plus CouchDB restart; verify after the restart and fail loudly
- [x] 2. postrm: remove the postinst-written ini on purge
- [x] 3. staging run.sh: drift scenario — rotate the credstore credential, reinstall, assert new password live and old one dead, rotate back
- [x] 4. staging smoke.sh: assert the credstore password authenticates and the written ini holds a hash, not plaintext
- [x] 5. Validate the mechanism in the staging container: local.d precedence (99 overrides the packaged admins ini) and CouchDB's hash write-back into the same file
