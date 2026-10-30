## Why

On iOS an app added to the home screen keeps its storage apart from Safari and opens `start_url`, so a recovery link never reaches it: it has no address bar, and links and QR codes open in Safari (#683). The installed app cannot join an account.

## Changes

- The sync button shows on the home page of a device without an account too. Its menu offers to paste a recovery link.
- A pasted link's fragment goes into the address and the app starts again, so the boot adopts the key exactly as from an opened link — `pair=` included.
- A text that is not a recovery link is refused with a short message; nothing changes.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `account-provisioning`: a key may be pasted as a recovery link, not only opened.
- `push-sync`: the sync control shows on the home page without an account too.

## Decisions

- The paste goes through the boot's path (fragment, then a restart) — over adopting the key on the running app, where the databases are already open and a wipe through an open handle hangs.
- The text is asked with the system `prompt`, as a new set's name is — over a dialog of our own.
- Only a `#key=` link is accepted; an `#invite=` link pasted here is refused, since the issue asks for recovery links alone.
- The no-account menu has only this item and Cancel; «Запросить инвайт» (#650) joins it later, and the sync icon's rework (#677) rebases over it.
