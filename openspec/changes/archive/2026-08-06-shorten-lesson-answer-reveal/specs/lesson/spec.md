## ADDED Requirements

### Requirement: Error reveal labels the correct answer with short copy
The lesson error footer SHALL label the revealed correct answer with the header «Правильно:».

#### Scenario: Wrong answer reveal
- **WHEN** a user answers a trial incorrectly
- **THEN** the footer shows the user's answer under «Ваш ответ:»
- **AND** the correct answer under «Правильно:»

### Requirement: Answer field suppresses text assistance and wraps long input
The lesson answer field SHALL be a multi-line textarea with browser text assistance disabled — `spellcheck="false"`, `autocorrect="off"`, `autocapitalize="none"`, `autocomplete="off"` — so German input is not corrected against the user, and long answers SHALL wrap onto new lines inside the field.

#### Scenario: Text assistance attributes present
- **WHEN** the lesson input footer renders
- **THEN** the answer field is a textarea with spellcheck, autocorrect, autocapitalize and autocomplete disabled

#### Scenario: Long answer wraps
- **WHEN** the typed answer exceeds the field width
- **THEN** the text soft-wraps onto further lines within the textarea
