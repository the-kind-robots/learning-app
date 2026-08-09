# Tasks

- [x] 1. Move the CouchDB steps inside the provisioning transaction; best-effort removal of the userdb a failed attempt created
- [x] 2. Tests: a failed CouchDB step leaves no row and no userdb; the next attempt provisions the same id cleanly
