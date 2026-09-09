## MODIFIED Requirements

### Requirement: Worktrees live where the agent tool puts them
Worktrees SHALL be created only through the agent's built-in mechanism, which keeps them under `.claude/worktrees/`, except where that mechanism cannot reach the branch the work must land on: an agent pinned to a worktree SHALL create its linked worktree inside the worktree it was given, since a path outside it is refused. The repository SHALL NOT define a second location for ordinary work, and the only ignore rule it SHALL carry for worktrees is the one that keeps such a nested worktree from being committed, because staging everything in the parent records the inner checkout as an embedded repository.

The choice between the main checkout and a worktree SHALL be a test on dependencies, not a match against a list of subsystems. Work SHALL run in the main checkout only where a concrete shared resource it depends on can be named — a live CouchDB, nginx routing, a migration against real data. Where none can be named, the work SHALL run in a worktree. Subsystem names SHALL appear only as examples of what usually needs the stand, never as the test itself: naming subsystems routes by what a task mentions rather than by what it needs, and a task only has to mention the dictionary to be sent to the main checkout without needing it.

A worktree SHALL be treated as able to verify browser behaviour on fixture data. It shares the origin and serves on `localhost`, which is a secure context, so OPFS, Web Locks, service workers and workers all function there. Only what genuinely depends on the real data or the shared services — cold-start timing against the full dictionary, replication against a live CouchDB, nginx routing — SHALL require the main checkout.

#### Scenario: Work needs isolation
- **WHEN** a task is isolated in a worktree
- **THEN** it is created by the built-in mechanism, and no worktree directory appears inside the repository tree

#### Scenario: Work needs the full stand
- **WHEN** a task depends on a shared resource that can be named — a live CouchDB, nginx routing, or a migration against real data
- **THEN** it runs in the main worktree, because a worktree isolates files while ports, CouchDB and nginx stay shared

#### Scenario: A stand subsystem is touched but no shared resource is needed
- **WHEN** a task touches a subsystem usually associated with the stand, such as the dictionary, but no live CouchDB, nginx routing or real-data migration can be named as a dependency
- **THEN** it runs in a worktree, because the test is the dependency and not the subsystem it belongs to

#### Scenario: Browser behaviour is verified on fixture data
- **WHEN** a task must prove browser-runtime behaviour that needs only one origin and browser APIs, such as two tabs sharing an OPFS database through a Web Lock
- **THEN** a worktree is sufficient, because `localhost` is a secure context and the fixture data the browser tests already use serves the check

#### Scenario: A pinned agent creates the only worktree it can use
- **WHEN** an agent pinned to a worktree adds a linked worktree inside it
- **THEN** staging everything in the parent worktree cannot commit that checkout, and closeout removes it together with the branch
