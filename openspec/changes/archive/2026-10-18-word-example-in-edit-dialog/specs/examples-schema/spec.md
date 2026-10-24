## ADDED Requirements

### Requirement: A scope shows one example of a word, and replacing it leaves one

A collection's example of a word is the one made for that collection with the smallest id. Inside a named collection the dialog shows one collection's example, that of the collection that lists the word: the active collection itself, else a collection of its family, so that a word that lies only in a child of the active folder shows the child's example.

In the main scope the dialog MAY show several collections' examples: one for every collection that lists the word, and one with no collection when the word lies in none or has an example that carries no `collection-id`. The dialog SHALL open on the pair of the example a lesson in the scope asks, else the first.

Replacing the example of a pair SHALL store the new example under the id its content gives, and then delete every other example document of that pair in one write, so the pair holds exactly the new one. A new example that cannot be stored SHALL delete nothing. The new example SHALL carry the collection of the pair, and SHALL carry no `structure` when the learner wrote it.

#### Scenario: Several examples in a scope
- **WHEN** a scope sees two examples of a word
- **THEN** the lesson and the edit dialog both show the one with the smaller id

#### Scenario: Replacing leaves one
- **WHEN** a pair holds two example documents and the learner replaces its example
- **THEN** the pair holds exactly the new one

#### Scenario: Examples in the main scope
- **WHEN** a word lies in the themes «Bahn» and «Alltag» and has an example with no collection
- **THEN** the main scope shows three collections' examples: «Alltag», «Bahn» and the one with no collection

#### Scenario: A word with no example
- **WHEN** the active collection lists the word and has no example for it
- **THEN** the dialog shows that collection's example, empty

#### Scenario: A word in a child of the active folder
- **WHEN** the active collection is the folder «Reisen», the word lies only in «Reisen/Bahn», and that collection has an example for it
- **THEN** the dialog shows the example of «Reisen/Bahn»
