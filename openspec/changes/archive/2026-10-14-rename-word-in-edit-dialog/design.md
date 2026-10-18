## Context

A vocab id is the normalised text (ADR-0008), so another spelling is another document. Renaming therefore replaces the document; the requirement is in `specs/learner-data-memory/spec.md`.

## Decisions

- Same id after normalisation: the existing translation update, no new document.
- Different id: one `change!` write of the new word, the old word deleted as `delete!` deletes it, and every collection repointed; `change!` retries and takes it back. The new word's initial review follows the add rule: none when the new id already has reviews.
- The presenter computes `renamed?`, `duplicate?`, the notice and the disabled state; the view only reads them.
- Direction of the screen: the approved design (https://claude.ai/artifact/DHXhbKRDRkZbn9XPnDpfd9), Nunito and the existing tokens. Only the word field, the notice, the duplicate message and the delete label are in this change; the example block is #141.
