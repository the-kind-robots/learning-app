## MODIFIED Requirements

### Requirement: The words and themes screens open and close from the keyboard

On the home screen `Alt`+`W` SHALL open the words screen and `Alt`+`T` the themes screen; on home, the words and the themes screens `Alt`+`S` SHALL open the statistics, as today's stars do. Each SHALL act wherever the focus sits, read by the key's place so that any keyboard layout works, and SHALL do nothing where its button is not shown: `Alt`+`W` with an empty vocabulary, `Alt`+`S` without a lesson today, each on any other screen. `Escape` on the words or themes screen SHALL close it, as its corner ✕ does, unless a dialog or a menu is open: then `Escape` closes only the dialog or the menu. While a menu or a dialog is open no screen's hotkey SHALL act.

#### Scenario: Alt+W from the word field

- **WHEN** the vocabulary has a word and the learner presses Alt+W in the home word field
- **THEN** the words screen opens and nothing is typed into the field

#### Scenario: Alt+T on home

- **WHEN** the learner presses Alt+T on the home screen
- **THEN** the themes screen opens

#### Scenario: Alt+S on home

- **WHEN** a learner with a lesson today presses Alt+S in the home word field
- **THEN** the statistics screen opens and nothing is typed into the field

#### Scenario: Alt+S on the words or themes screen

- **WHEN** a learner with a lesson today presses Alt+S on the words or themes screen
- **THEN** the statistics screen opens over it

#### Scenario: Alt+S without a lesson today

- **WHEN** a learner with no lesson today presses Alt+S on home
- **THEN** the home screen stays

#### Scenario: Alt+W with an empty vocabulary

- **WHEN** the vocabulary is empty and the learner presses Alt+W on home
- **THEN** the home screen stays

#### Scenario: Escape on the words or themes screen

- **WHEN** the learner presses Escape on the words or themes screen
- **THEN** the home screen is shown

#### Scenario: Escape in the word dialog

- **WHEN** the word dialog is open and the learner presses Escape
- **THEN** the dialog closes and the words screen stays

#### Scenario: Escape in the settings menu

- **WHEN** the settings menu is open over the words screen and the learner presses Escape
- **THEN** the menu closes and the words screen stays
