## ADDED Requirements

### Requirement: Every collection of a word shows its own example, and replacing it leaves one

A collection's example of a word is the one made for that collection with the smallest id. The dialog SHALL show one for every collection that lists the word, and one with no collection when the word lies in none or has an example that carries no `collection-id`, last. The collection of the scope SHALL come first: the active collection when it lists the word, else a collection of its family that does, so that a word that lies only in a child of the active folder shows the child's example; the rest follow oldest first.

Replacing the example of a pair SHALL store the new example under the id its content gives, and then delete every other example document of that pair in one write, so the pair holds exactly the new one. A new example that cannot be stored SHALL delete nothing. The new example SHALL carry the collection of the pair, and SHALL carry no `structure` when the learner wrote it.

#### Scenario: Replacing leaves one
- **WHEN** a pair holds two example documents and the learner replaces its example
- **THEN** the pair holds exactly the new one

#### Scenario: A collection with no example
- **WHEN** a collection lists the word and has no example for it
- **THEN** the dialog shows its card, empty

#### Scenario: A word in a child of the active folder
- **WHEN** the active collection is the folder «Reisen», the word lies only in «Reisen/Bahn», and that collection has an example for it
- **THEN** the dialog shows the card of «Reisen/Bahn» first, with that example

## REMOVED Requirements

### Requirement: A scope shows one example of a word, and replacing it leaves one
**Reason**: The dialog shows every collection's example, not the scope's alone.
**Migration**: «Every collection of a word shows its own example, and replacing it leaves one».
