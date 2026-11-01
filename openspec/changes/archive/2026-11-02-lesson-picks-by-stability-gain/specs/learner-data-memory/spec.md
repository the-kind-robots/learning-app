## MODIFIED Requirements

### Requirement: Deleting a word keeps its reviews and examples
Deleting a word SHALL delete the word document and take its id out of collections as `specs/collections-data-model/spec.md` states. Which collections list it SHALL be asked of memory once it has caught up. When PouchDB refuses any document of that write, the delete SHALL read them again and write once more; refused again, the delete SHALL fail: it SHALL be reported, and a word that still exists SHALL be listed in every collection that listed it.

Deleting a word SHALL NOT delete its reviews or its examples (a collection keeps its examples as `specs/collections-data-model/spec.md` states): when a deleted word is added again, its reviews and its examples SHALL come back with it as its history. A word added, for the first time or again, SHALL get no review, and an example SHALL be fetched for it only when the active collection has none. After a synchronisation pass, a word or a collection the pass deleted SHALL get no example fetched for it.

#### Scenario: No collection keeps a deleted word
- **WHEN** a word is deleted, and another device has just put it into a collection memory has not seen
- **THEN** no collection lists the word once the delete is done

#### Scenario: A delete PouchDB refuses twice
- **WHEN** PouchDB refuses the deletion of the word twice
- **THEN** the delete is reported as failed
- **AND** the word exists and is listed in its collections as before

#### Scenario: A word added again
- **WHEN** the learner deletes a word and adds it again
- **THEN** the word has the reviews it had before the delete and no new one, and its retention is computed from them
- **AND** no example is fetched for it in «Всё подряд», where it has one already

#### Scenario: A word re-created on another device
- **WHEN** a word deleted here earlier arrives again by a pull, with reviews made on another device
- **THEN** those reviews and the earlier ones are kept, on this start and on every later one

### Requirement: A word's spelling is corrected in its edit dialog
The edit dialog SHALL let the user change the text of a word or phrase. A new text with the same vocab id (differing only as normalisation ignores) SHALL be saved as an ordinary edit of the same word. A new text with another vocab id SHALL be saved as a new word with the same translation, of the kind its new text gives, in every collection that listed the old word, and the old word SHALL be deleted as a delete does (`Deleting a word keeps its reviews and examples`), all in one write. The new word SHALL get no review. A text whose vocab id belongs to another word SHALL be refused: the dialog SHALL say «Такое слово уже есть» and SHALL NOT allow saving. While the text differs from the word's own, the dialog SHALL say that it is saved as a new word in the same themes and that the old word goes with its progress and examples.

#### Scenario: A typo is corrected
- **WHEN** the word «Bahnhoff» is in the themes «Bahn» and «Alltag» and the user saves it as «Bahnhof»
- **THEN** the list shows «Bahnhof» and not «Bahnhoff», both themes list «Bahnhof», and after a reload «Bahnhoff» is still gone

#### Scenario: A change of case is an ordinary edit
- **WHEN** the user saves «bahnhof» as «Bahnhof»
- **THEN** the word keeps its id, its progress and its examples, and no notice about a new word was shown

#### Scenario: The spelling is already a word
- **WHEN** the user types a spelling that is another word of the vocabulary
- **THEN** the dialog says «Такое слово уже есть» and saving is disabled
