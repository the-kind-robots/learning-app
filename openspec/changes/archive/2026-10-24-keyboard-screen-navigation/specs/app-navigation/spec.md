## ADDED Requirements

### Requirement: The words and themes screens open and close from the keyboard

On the home screen `Alt`+`W` SHALL open the words screen and `Alt`+`T` the themes screen, wherever the focus sits, read by the key's place so that any keyboard layout works. Each SHALL do nothing where its button is not shown: `Alt`+`W` with an empty vocabulary, both on any other screen. `Escape` on the words or themes screen SHALL close it, as its corner ✕ does, unless a dialog is open: then `Escape` closes only the dialog.

#### Scenario: Alt+W from the word field

- **WHEN** the vocabulary has a word and the learner presses Alt+W in the home word field
- **THEN** the words screen opens and nothing is typed into the field

#### Scenario: Alt+T on home

- **WHEN** the learner presses Alt+T on the home screen
- **THEN** the themes screen opens

#### Scenario: Alt+W with an empty vocabulary

- **WHEN** the vocabulary is empty and the learner presses Alt+W on home
- **THEN** the home screen stays

#### Scenario: Escape on the words or themes screen

- **WHEN** the learner presses Escape on the words or themes screen
- **THEN** the home screen is shown

#### Scenario: Escape in the word dialog

- **WHEN** the word dialog is open and the learner presses Escape
- **THEN** the dialog closes and the words screen stays

### Requirement: The words screen opens with the cursor in its search field

On a device with a fine pointer the words screen SHALL open with the focus in its search field. On a touch screen it SHALL NOT, so no keyboard rises.

#### Scenario: Words screen opened on a computer

- **WHEN** the learner opens the words screen on a computer
- **THEN** the search field has the focus and typed letters search
