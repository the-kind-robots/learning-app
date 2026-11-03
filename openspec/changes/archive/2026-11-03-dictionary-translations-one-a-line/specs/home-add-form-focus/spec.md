## MODIFIED Requirements

### Requirement: The prefilled translation is the dictionary's text as stored
When the dictionary fills the translation field — from the entry the list marks on its own while no translation is the user's, or on the user picking an entry — the field SHALL receive the entry's translations one a line, each trimmed, in the dictionary's order. The field is read one translation a line when the word is added, so each of the dictionary's translations SHALL be stored as a translation of its own, as if typed so. The dictionary carries its translations as discrete elements; a translation containing a comma SHALL stay one line.

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
