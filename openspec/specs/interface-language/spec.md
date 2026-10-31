# interface-language Specification

## Purpose
Define which language the interface speaks and how the page declares it.

## Requirements

### Requirement: The page declares the interface language
The page SHALL carry the language of the interface in `<html lang>`. The document the server sends SHALL declare the language that the request's first `Accept-Language` tag gives by the first-run rule (Requirement: Without a choice the language follows the system); once the client renders, `<html lang>` SHALL be the learner's language.

#### Scenario: Opening the application
- **WHEN** a user whose system language is Russian opens the application
- **THEN** the `<html>` element has `lang="ru"`
- **AND** the home screen reads in Russian: its heading is «Главная» and the word field's placeholder is «Новое слово»

#### Scenario: The server's page on a system without its dictionary
- **WHEN** the browser asks for the page with `Accept-Language: en-GB` and the build has no English dictionary
- **THEN** the document the server sends has `<html lang="ru">`

### Requirement: A language is there when its dictionary is
The languages of the interface SHALL be the dictionaries the build carries, one `i18n/<language>.edn` each, named by its two-letter code: adding the file SHALL add the language, with no other change, and the file's `:language/name` SHALL name it in the settings, in its own language. Russian SHALL always be there. A key a dictionary lacks SHALL read as the Russian text of that key.

#### Scenario: A dictionary is added
- **WHEN** the build carries `en.edn` beside `ru.edn`
- **THEN** the settings offer English and Русский, and the first-run rule can give English

#### Scenario: A key not translated yet
- **WHEN** the learner's language is English and `en.edn` lacks `:home/subtitle`
- **THEN** that line of the home screen reads in Russian and the rest in English

### Requirement: The learner picks the interface language
Pressing the word mark «Sprecha» in the bar, on any screen, SHALL open the settings «Настройки» as a menu under it, the screen staying as it was behind it; pressing it again, pressing anywhere outside the menu, or Escape SHALL close it. The menu SHALL list every language as one group of radios named «Язык», the name not shown, each by its own name with its flag at the row's end, where a check used to be, the learner's language checked and told apart by its fill and a bolder name, with no check mark, also when Russian is the only one. The word mark's name SHALL be «Sprecha, настройки». Checking a language SHALL re-render the interface in it at once, the menu staying open, and store it in the settings document (data-model, Requirement: The learner's settings are one document), so that every device of the account follows it. A stored language the build has no dictionary for is a pick another device's build can show: this one SHALL speak the language the first-run rule gives and check it in the settings, SHALL NOT ask, and SHALL store nothing until the learner checks a language, so that the pick shows again once this build carries it.

#### Scenario: Switching the language
- **WHEN** the build carries English and the learner presses «Sprecha» and then English
- **THEN** English is checked, Русский is not, and `<html lang>` is `en`
- **AND** the next start opens in English

#### Scenario: One language
- **WHEN** the build carries Russian alone and the learner presses «Sprecha»
- **THEN** the group «Язык» holds Русский, checked

#### Scenario: The menu's rows
- **WHEN** the learner presses «Sprecha»
- **THEN** each language shows its flag after its name, and no heading «Язык» and no check mark are on screen

#### Scenario: Closing the settings
- **WHEN** the learner presses outside the open settings, presses Escape, or presses «Sprecha» again
- **THEN** the settings close and the screen is as it was

#### Scenario: Another device's choice arrives
- **WHEN** the settings document changes by replication to a language the build carries
- **THEN** the interface re-renders in it

#### Scenario: A choice without a dictionary
- **WHEN** the settings document holds French, picked on a device whose build has a French dictionary, and this build has none
- **THEN** the interface speaks the language the first-run rule gives, and the settings check it
- **AND** a German system is not asked, and the document keeps French

### Requirement: Without a choice the language follows the system
While no settings document holds a language, or it holds one the build has no dictionary for, the learner's language SHALL be the system language (`navigator.language`, its primary subtag) when the build carries it, else English when the build carries it, else Russian — nothing SHALL be stored for it. A German system with no settings document SHALL be offered the settings at start when there are languages to choose from, with the language the rule gives checked and focused and no way to close them but a pick, since German is the language being learned and never the interface's; the pick SHALL be stored like any other and close the settings.

#### Scenario: A system whose language has a dictionary
- **WHEN** a learner without a stored language starts the app on a `tr-TR` system and the build carries Turkish
- **THEN** the interface is in Turkish and nothing is stored

#### Scenario: A system whose language has none
- **WHEN** a learner without a stored language starts the app on a `ja-JP` system
- **THEN** the interface is in English when the build carries English, else in Russian

#### Scenario: A German system
- **WHEN** a learner without a stored language starts the app on a `de-DE` system and the build carries English and Russian
- **THEN** the settings are open with English checked and focused, and neither Escape nor a press outside them closes them
- **AND** picking a language, the checked English too, closes it and stores the pick, so the next start opens in it without asking

#### Scenario: A German system with one language
- **WHEN** a learner without a stored language starts the app on a `de-DE` system and the build carries Russian alone
- **THEN** the settings are not open and the interface is in Russian
