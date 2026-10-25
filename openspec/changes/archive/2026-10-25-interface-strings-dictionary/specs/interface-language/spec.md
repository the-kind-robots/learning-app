## ADDED Requirements

### Requirement: The page declares the interface language
The document the server sends SHALL carry the language of the interface's text in `<html lang>`. While Russian is the only interface language, that value SHALL be `ru`.

#### Scenario: Opening the application
- **WHEN** the user opens the application
- **THEN** the `<html>` element has `lang="ru"`
- **AND** the home screen reads in Russian: its heading is «Главная» and the word field's placeholder is «Новое слово»
