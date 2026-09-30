## MODIFIED Requirements

### Requirement: The active collection persists across restarts
The system SHALL remember the id of the active collection across restarts. The active collection SHALL be the collection the learner's data holds under the remembered id. With no id remembered, or with an id under which the learner's data holds no collection — a collection deleted on this device or on another — «Всё подряд» SHALL be the active one, on every screen and in every action that follows the active collection.

#### Scenario: Active collection restored on app load
- **WHEN** the app starts and a collection id is remembered
- **THEN** that collection is the active one

#### Scenario: Deleting the active collection
- **WHEN** the active collection is deleted
- **THEN** «Всё подряд» is the active one

#### Scenario: A remembered collection deleted on another device
- **WHEN** the app starts and the remembered id names a collection the learner's data no longer holds
- **THEN** home, the words list, a lesson, the themes screen, removing a word and adding a word all act as with «Всё подряд» active
