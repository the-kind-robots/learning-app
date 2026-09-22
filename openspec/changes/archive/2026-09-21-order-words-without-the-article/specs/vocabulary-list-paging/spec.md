## MODIFIED Requirements

### Requirement: The word list is ordered alphabetically

The word list SHALL show words in alphabetical order, by the normalised form the word is
stored under with a leading German definite article — `der`, `die`, `das` — left out of
the comparison: case, umlaut spelling and the article do not split the order, and no other
ranking — how due a word is, how well it is remembered — decides where a row sits. The row
SHALL still show the value as it was entered, article and all. The order SHALL NOT be
German dictionary collation; nothing beyond the article is read to improve on the stored
form.

#### Scenario: Nouns and other words in one alphabet

- **WHEN** the list holds `der Hund`, `die Katze`, `das Auto`, `der Zug`, `die Bank` and
  `aufstehen`
- **THEN** they are shown as `aufstehen`, `das Auto`, `die Bank`, `der Hund`, `die Katze`,
  `der Zug`
- **AND** each row shows the article it was entered with

#### Scenario: A page past the first

- **WHEN** the reader loads a page of the list after the first one
- **THEN** its rows continue the same order, with no word repeated and none skipped

#### Scenario: Words of different retention

- **WHEN** the list holds words that were last reviewed days and months apart
- **THEN** they are shown in alphabetical order, whatever their retention levels

#### Scenario: The lesson is not affected

- **WHEN** a lesson is started
- **THEN** it draws the words most in need of review, not the alphabetically first ones

#### Scenario: A word is still found by the value it was entered under

- **WHEN** a word entered with an article is looked up, edited or added a second time
- **THEN** it is the same entry as before, since what a word is filed under does not
  change what it is stored under
