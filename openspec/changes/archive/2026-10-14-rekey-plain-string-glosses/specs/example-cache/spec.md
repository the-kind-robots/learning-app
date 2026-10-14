## ADDED Requirements

### Requirement: Rows with a plain-string gloss are re-keyed as one gloss

The re-key SHALL read a row's glosses as a JSON array, or, when the column holds a plain string, as that one gloss, whole and without splitting on commas, exactly as a request's single `translation` string is read. A row whose glosses are neither a JSON array of strings nor a non-blank string SHALL be skipped and left waiting.

#### Scenario: A plain-string row

- **WHEN** a row stores its glosses as the plain string `собака`
- **AND** the re-key has finished
- **THEN** a request for the same word with the translation `собака` is served from that row

#### Scenario: A plain string with commas

- **WHEN** a row stores its glosses as the plain string `собака, пёс`
- **THEN** it is re-keyed as the one gloss `собака, пёс`, not as two

#### Scenario: Glosses that cannot be read

- **WHEN** a row's glosses are blank or a JSON array with a non-string
- **THEN** the row is skipped, counted as skipped, and left waiting
