## MODIFIED Requirements

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
