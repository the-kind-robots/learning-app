## Decision

The id is encoded where it is built, as a path segment (space as `%20`); `db/apply-conn` is left alone because other callers pass query strings in `:url`. A dictionary read failure still fails the generation (`503`): after this fix such failures are transient, and degrading would cache a lower-quality example.
