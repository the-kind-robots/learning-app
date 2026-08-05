# Tasks

- [x] 1. Per-run unique temp upload path inside /opt/learning-app
- [x] 2. sha256 recorded at build, verified on the server before the rename
- [x] 3. Stale temp cleanup after a successful swap
- [x] 4. Validate workflow YAML; dry-run the server-side script against a simulated /opt/learning-app (happy path, torn upload, missing upload)
- [ ] 5. Watch the next real deploy: checksum line `OK` in the ssh step, service restarts once on a whole jar
