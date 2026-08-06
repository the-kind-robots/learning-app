# deploy-pipeline Specification

## ADDED Requirements

### Requirement: Configuration shipped in the deb is in effect when postinst finishes

When the deb ships configuration that a running daemon reads only at startup, postinst SHALL restart that daemon if and only if the shipped content changed since the previous install, and SHALL do so before any postinst step that depends on the daemon being ready. Installs that ship unchanged content SHALL NOT restart the daemon.

#### Scenario: An upgrade changes the CouchDB ini

- **WHEN** the deb installs over a running CouchDB and the shipped `10-learning-app.ini` differs from the content stamped at the previous install
- **THEN** postinst restarts couchdb.service before its readiness wait and admin configuration, and the new configuration is live when postinst exits

#### Scenario: A re-install ships the same ini

- **WHEN** the deb installs and the shipped ini matches the stamp
- **THEN** CouchDB is not restarted
