## ADDED Requirements

### Requirement: The translation field takes several lines and submits on a modifier
The translation field SHALL accept a multi-line translation. A bare `Enter` SHALL insert a newline and SHALL NOT submit the form; `Ctrl`+`Enter` or `Cmd`+`Enter` SHALL submit it. The submit button SHALL keep submitting the form, since it is the only path on a phone, where neither modifier can be typed. `Enter` on the German word input keeps its own meaning and SHALL NOT be affected.

#### Scenario: Enter inserts a newline
- **WHEN** the translation field has focus
- **AND** the user presses `Enter` with no modifier
- **THEN** the form is not submitted
- **AND** the field takes a newline

#### Scenario: Ctrl+Enter submits
- **WHEN** the translation field has focus
- **AND** the user presses `Enter` while `Ctrl` is held
- **THEN** the form is submitted
- **AND** the keystroke's default action is suppressed, so the form is submitted once

#### Scenario: Cmd+Enter submits
- **WHEN** the translation field has focus
- **AND** the user presses `Enter` while `Cmd` (meta) is held
- **THEN** the form is submitted

#### Scenario: The button submits without a keyboard
- **WHEN** the user activates the add button
- **THEN** the form is submitted, whatever the translation field holds

#### Scenario: The German input keeps its Enter
- **WHEN** the German word input has focus
- **AND** the user presses `Enter`
- **THEN** the existing behaviour applies — the highlighted suggestion is picked, or the form submits when there is none

### Requirement: The prefilled translation is the dictionary's text as stored
When the dictionary fills the translation field — on a suggestion arriving for an untouched field, or on the user picking a suggestion — the field SHALL receive the dictionary's translation text as the dictionary holds it. The transport splits a lemma's translations on `,` and leaves the following space on the next piece, so the form SHALL trim the pieces before rejoining them on `, `. What the field shows is what gets stored, so the reconstruction SHALL neither drop nor double a space.

#### Scenario: A stored translation containing a comma is prefilled whole
- **WHEN** the dictionary's translation for a lemma is `без того, чтобы`
- **AND** that lemma's suggestion fills the translation field
- **THEN** the field holds `без того, чтобы`, with no doubled space after the comma

#### Scenario: Several translations stay readable
- **WHEN** a lemma has the translations `пёс` and `собака`
- **THEN** the field holds `пёс, собака`

#### Scenario: Picking a suggestion fills the same text
- **WHEN** the user picks a suggestion from the list
- **THEN** the translation field receives that entry's translation text by the same rule
