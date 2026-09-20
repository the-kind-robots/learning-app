## Why

Creating a collection refuses a name another collection carries (trimmed, case-insensitive), but renaming does not, so two documents can end up under one name — seen on a phone as a folder tile plus a stray tile of the same name after renaming a parent away and back.

## What Changes

- Renaming a collection to a name another collection carries — by the equality create and the folder lookup use — is refused: the current name stays and nothing is written, shown the way a blank name is.
- A collection's name is unique, trimmed and case-insensitively, across create and rename.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `collections-data-model`: names are unique across create and rename.
- `collections-navigation`: rename refuses a taken name.

## Impact

- Affected specs: `collections-data-model`, `collections-navigation`.
- Affected code: `use-cases.collections/rename-active!`, its node test, the tiles browser spec.
