## 1. Client

- [x] 1.1 The snapshot body is memory as `memory/stored` gives it, with a transit handler for review histories; the word list is rebuilt on restore
- [x] 1.2 Memory finds the version of a document it holds by the document's id, and keeps no entries or revisions
- [x] 1.3 The format version is 3

## 2. Tests

- [x] 2.1 The golden test of the format holds the stored memory
- [x] 2.2 Restored memory equals rebuilt memory in retention levels, lesson picks and everything memory holds
- [x] 2.3 The property tests run snapshot and catch-up on the new format
