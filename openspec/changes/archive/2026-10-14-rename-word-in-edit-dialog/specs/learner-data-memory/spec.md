## ADDED Requirements

### Requirement: A word's spelling is corrected in its edit dialog
The edit dialog SHALL let the user change the text of a word or phrase. A new text with the same vocab id (differing only as normalisation ignores) SHALL be saved as an ordinary edit of the same word. A new text with another vocab id SHALL be saved as a new word with the same translation, of the kind its new text gives, in every collection that listed the old word, and the old word SHALL be deleted as a delete does (`Deleting a word keeps its reviews and examples`), all in one write. The new word SHALL get an initial review unless it has reviews already. A text whose vocab id belongs to another word SHALL be refused: the dialog SHALL say «Такое слово уже есть» and SHALL NOT allow saving. While the text differs from the word's own, the dialog SHALL say that it is saved as a new word in the same themes and that the old word goes with its progress and examples.

#### Scenario: A typo is corrected
- **WHEN** the word «Bahnhoff» is in the themes «Bahn» and «Alltag» and the user saves it as «Bahnhof»
- **THEN** the list shows «Bahnhof» and not «Bahnhoff», both themes list «Bahnhof», and after a reload «Bahnhoff» is still gone

#### Scenario: A change of case is an ordinary edit
- **WHEN** the user saves «bahnhof» as «Bahnhof»
- **THEN** the word keeps its id, its progress and its examples, and no notice about a new word was shown

#### Scenario: The spelling is already a word
- **WHEN** the user types a spelling that is another word of the vocabulary
- **THEN** the dialog says «Такое слово уже есть» and saving is disabled
