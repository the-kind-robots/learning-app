# Tasks

## 1. Implement

- [x] 1.1 `-main` dispatches on its first argument: none serves, `mint-invite` mints, anything else exits nonzero
- [x] 1.2 `mint-invite` adopts and migrates the configured database before inserting the grant, prints the invite URL, starts no server
- [x] 1.3 Invite URL built from `LEARNING_APP__PUBLIC_URL` (default canonical origin), token in the fragment

## 2. Verify

- [x] 2.1 Backend tests: URL composition, dispatcher mints a burnable grant, no server comes up
- [x] 2.2 Live run against a scratch database: URL printed, grant row present, process exits on its own
