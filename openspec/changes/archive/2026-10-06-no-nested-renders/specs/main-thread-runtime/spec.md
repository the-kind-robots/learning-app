## ADDED Requirements

### Requirement: A tab shown again keeps the screen it rendered
A life-cycle hook (`:replicant/on-mount`, `:replicant/on-render`, `:replicant/on-unmount`) SHALL NOT write the store: a DOM node is not state. A tab that is brought on screen SHALL keep showing the screen it last rendered, and no older screen SHALL be painted over it.

#### Scenario: Opening the app in a development build
- **WHEN** the app is opened on any screen in a development build
- **THEN** the console shows no Replicant report "Triggered a render while rendering"

#### Scenario: A tab loads while it is not on screen
- **WHEN** the app loads in a tab that is not on screen and home is rendered
- **AND** the tab is then brought on screen
- **THEN** home is still on display, not the splash
