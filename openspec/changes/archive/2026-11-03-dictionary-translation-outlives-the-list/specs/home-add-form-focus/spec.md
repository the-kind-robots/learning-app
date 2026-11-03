## MODIFIED Requirements

### Requirement: The prefilled translation is the dictionary's text as stored
When the dictionary fills the translation field — from the entry the list marks on its own while no translation is the user's, or on the user picking an entry — the field SHALL receive the entry's translations one a line, each trimmed, in the dictionary's order. The field is read one translation a line when the word is added, so each of the dictionary's translations SHALL be stored as a translation of its own, as if typed so. The dictionary carries its translations as discrete elements; a translation containing a comma SHALL stay one line. While no translation is the user's, the prefill SHALL change only when the dictionary answers for what the word field holds: closing the list — `Escape`, or the word input losing focus to the translation field or the submit button — and a keystroke before the answer SHALL leave it as it is.

#### Scenario: A stored translation containing a comma is prefilled whole
- **WHEN** the dictionary's translation for a lemma is `без того, чтобы`
- **AND** that lemma's suggestion fills the translation field
- **THEN** the field holds `без того, чтобы` on one line

#### Scenario: Several translations stay readable
- **WHEN** a lemma has the translations `кошка` and `кот`
- **THEN** the field holds `кошка` and `кот` on two lines
- **AND** adding the word stores them as two translations

#### Scenario: Picking a suggestion fills the same text
- **WHEN** the user picks a suggestion from the list
- **THEN** the translation field receives that entry's translations by the same rule

#### Scenario: Closing the list keeps the prefill
- **WHEN** the dictionary has filled the translation field for `Katz`
- **AND** the user presses `Escape`, or moves focus to the translation field
- **THEN** the list closes and the field still holds `кошка` and `кот`

#### Scenario: Adding right after the prefill adds it
- **WHEN** the dictionary has filled the translation field
- **AND** the user presses the submit button
- **THEN** the word is added with that translation

#### Scenario: A keystroke waits for the answer
- **WHEN** the field holds `спина`, prefilled for `Rücken`
- **AND** the user types `m`
- **THEN** the field holds `спина` until the dictionary answers for `Rückenm`
- **AND** then `спинной мозг`
