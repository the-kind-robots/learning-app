## MODIFIED Requirements

### Requirement: Vocab conflict resolution unions translations
The system SHALL resolve a vocab document revision conflict by unioning the translations of the conflicting revisions, not by plain last-write-wins. The other fields SHALL be those of the revision last written by `modified-at`; between revisions written at the same `modified-at`, the higher revision SHALL win. The translations SHALL be listed from the oldest revision to the newest. The order the conflicting revisions are read in SHALL change nothing, so every device resolving the same conflict writes the same word.

#### Scenario: Concurrent translations merge
- **WHEN** two devices add the same word with different translations and sync
- **THEN** the resolved document contains the union of both translation sets
- **AND** neither side's translation is dropped

#### Scenario: Revisions written at the same time resolve the same on every device
- **WHEN** two revisions of a word carry the same `modified-at`
- **THEN** the resolved document has the fields of the higher revision
- **AND** it is the same whichever revision is read first
