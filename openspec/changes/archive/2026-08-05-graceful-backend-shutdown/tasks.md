# Tasks

- [x] 1. Drain timeout in `stop-server!`; `restart-server!` delegates to it
- [x] 2. `defonce`-guarded JVM shutdown hook calling `stop-server!`
- [x] 3. Test: stopping the server drains an in-flight request, then the socket refuses
- [x] 4. Live proof: SIGTERM on a free port logs "Server stopped", process exits, port closed
