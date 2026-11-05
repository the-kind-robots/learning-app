## ADDED Requirements

### Requirement: The main button stands still while the screen changes

On a phone, while a screen slides in or out, or the lesson fades in or out, the big button at the bottom of the screen SHALL stay where it stands, as the bar across the top does, and SHALL NOT move with either screen. Where both screens have one, its label and colour SHALL change in place, the old fading into the new over 200 ms, a label that does not change showing no flicker. Where only one of the screens has one, it SHALL fade in or out where it stands.

#### Scenario: From home to the words list

- **WHEN** the user taps «Список слов» on home on a phone
- **THEN** «НАЧАТЬ УРОК» stays where it is while the words screen enters from the right

#### Scenario: Into a lesson

- **WHEN** the user taps «НАЧАТЬ УРОК» on home on a phone
- **THEN** the button stays where it is, «НАЧАТЬ УРОК» fading into «ПРОВЕРИТЬ», while the screens fade through
