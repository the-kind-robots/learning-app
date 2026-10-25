## ADDED Requirements

### Requirement: The learner's settings are one document
The system SHALL store the learner's settings as one document in `user-db`, with `_id` `"settings"` and `type` `"settings"`, so that it replicates with the rest of the account's data. The document SHALL hold the learner's language in `language`, one of `"ru"`, `"en"`, `"tr"`, `"fr"`. It SHALL be written only when the learner picks a language; a device where no pick was made SHALL hold no settings document. When two devices store different picks, the winner replication picks SHALL be the learner's language on every device.

#### Scenario: Settings document shape
- **WHEN** the learner picks English
- **THEN** user-db holds `{"_id": "settings", "type": "settings", "language": "en"}` with its revision

#### Scenario: No pick, no document
- **WHEN** the app starts and the learner never picks a language
- **THEN** user-db holds no document with `_id` `"settings"`
