## MODIFIED Requirements

### Requirement: Text and controls stay readable in both schemes

In the dark scheme, body and secondary text SHALL have a contrast ratio of at least 4.5:1 against the
surface it stands on, and a control's label SHALL have at least 4.5:1 against the control. No
component SHALL keep a colour of the light scheme that is unreadable on the dark ground. The word tiles of
the lesson's example trial SHALL stand out from the ground and from the answer field in the dark scheme:
the sheet `#261b33` of menus and hints, ringed in light.

#### Scenario: A primary button in the dark scheme

- **WHEN** a primary button is shown in the dark scheme
- **THEN** its label has a contrast ratio of at least 4.5:1 against the button

#### Scenario: Answer feedback in the dark scheme

- **WHEN** the lesson marks an answer correct or wrong in the dark scheme
- **THEN** the feedback text has a contrast ratio of at least 4.5:1 against its background

#### Scenario: The example trial's tiles in the dark scheme

- **WHEN** the lesson shows an example trial in the dark scheme, some words already laid into the answer field
- **THEN** every tile, over the field and in it, is `#261b33` with a light ring
