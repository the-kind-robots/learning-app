# identity-provisioning Delta

## ADDED Requirements

### Requirement: An operator mints an invite from the packaged app

The packaged application SHALL expose a `mint-invite` command-line entry point that stores a single-use grant in the configured database, prints the resulting invite URL, and exits without starting the server. The URL SHALL point at the configured public origin and carry the token in the URL fragment, keeping it out of access logs and Referer headers.

#### Scenario: Minting from the jar

- **WHEN** the operator runs the jar with the `mint-invite` argument
- **THEN** database adoption and migrations run first, a single-use grant is stored, the invite URL is printed, and no server starts

#### Scenario: Configured public origin

- **WHEN** `LEARNING_APP__PUBLIC_URL` is set
- **THEN** the printed URL uses that origin, with no doubled slash before the fragment

#### Scenario: Unknown command

- **WHEN** the entry point receives an argument that is not a known command
- **THEN** the process names the unknown command on stderr and exits nonzero
