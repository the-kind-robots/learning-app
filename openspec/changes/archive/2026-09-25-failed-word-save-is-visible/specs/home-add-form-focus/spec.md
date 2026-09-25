## ADDED Requirements

### Requirement: A failed save says so and keeps the input
When saving from the home add form fails, the form SHALL show, under its fields,
«Слово не сохранилось: в приложении сбой, и это не ваша ошибка.» for a word,
«Фраза не сохранилась: в приложении сбой, и это не ваша ошибка.» for a phrase,
whatever the cause, and SHALL keep the typed value and translation. Every add
error SHALL carry text in the same place; a border alone is not enough. A save
that succeeds SHALL clear the text.

#### Scenario: The write fails
- **WHEN** the user submits a word and a translation and the write fails
- **THEN** the form shows «Слово не сохранилось: в приложении сбой, и это не ваша ошибка.»
- **AND** the word and translation fields still hold what was typed

#### Scenario: A later save succeeds
- **WHEN** the error text is on display and the user submits again and the write succeeds
- **THEN** the error text is gone and the form is reset for the next word
