# backend-configuration Delta

## ADDED Requirements

### Requirement: Dictionary artifact source is configurable through the environment

The backend SHALL serve the dictionary manifest and SQLite artifact from the
directory named by `LEARNING_APP__DICTIONARY_DIR` when it is set, and from its
own classpath resource when it is not. The served ETag, `Content-Hash`,
`Cache-Control` and 404 behaviour SHALL be identical in both cases. Production
SHALL leave the variable unset: a deployment can be held to the artifact inside
its own jar, never to a path on the host.

#### Scenario: Test stand serves a fixture

- **WHEN** the backend starts with `LEARNING_APP__DICTIONARY_DIR` pointing at a
  directory holding `manifest.edn` and `dictionary.sqlite`
- **THEN** `/dictionary/manifest` answers with that manifest's hash and the
  content-addressed file downloads from that directory

#### Scenario: Variable unset

- **WHEN** the backend starts without the variable
- **THEN** it serves the classpath dictionary with the headers it served before
  the variable existed

#### Scenario: Directory has no manifest

- **WHEN** the variable points somewhere without a `manifest.edn`
- **THEN** both dictionary routes answer 404, as a missing resource already
  does, and the backend still boots
