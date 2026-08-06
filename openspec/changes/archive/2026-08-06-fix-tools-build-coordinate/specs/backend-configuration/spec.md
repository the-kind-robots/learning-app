# backend-configuration Specification

## ADDED Requirements

### Requirement: Git dependency coordinates are internally consistent
Every `:git/tag` + `:git/sha` coordinate in the repository SHALL name the same commit: the sha SHALL be a prefix of the commit the tag points to. A coordinate SHALL be proven by resolving it with cold dependency caches, not only on machines whose caches already hold the commit.

#### Scenario: A pinned git dependency is bumped
- **WHEN** a `:git/tag` is changed in any `deps.edn`
- **THEN** the paired `:git/sha` is updated to the commit that tag dereferences to, and the build resolves from a clean gitlibs and maven cache

#### Scenario: Tag and sha disagree
- **WHEN** a coordinate pins a tag with the sha of a different commit
- **THEN** a clean-cache build fails, and the fix is aligning the sha with the tag
