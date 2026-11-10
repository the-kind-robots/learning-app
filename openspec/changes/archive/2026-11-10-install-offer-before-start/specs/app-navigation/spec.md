## ADDED Requirements

### Requirement: The shell bar offers installing whenever the browser does

The shell bar SHALL show its install button once the browser has offered to install the app, whenever in the page's life the offer came: an offer made while the app is still starting SHALL show the button on the first screen.

#### Scenario: The offer comes while the app starts

- **WHEN** the browser offers to install the app before the app has opened the learner's data
- **THEN** the install button shows in the shell bar of the first screen

#### Scenario: The offer comes on an open screen

- **WHEN** the browser offers to install the app while the home screen is shown
- **THEN** the install button shows in the shell bar
