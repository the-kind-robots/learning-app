## REMOVED Requirements

### Requirement: The device-sync control is on the home page only
**Reason**: The control appeared and disappeared beside the word mark as the learner moved between screens (#767).
**Migration**: Replaced by «The device-sync control is on every screen».

## ADDED Requirements

### Requirement: The device-sync control is on every screen
The system SHALL offer the device-sync control, named «Синхронизация», in the header of every screen, with or without an account, once the learner's data is loaded. The control SHALL draw a laptop beside a phone and SHALL stand right after the word mark, centred on its line; it SHALL take a tap within a 44 px square around it without growing. A screen whose data could not be read SHALL render no such control.

The control SHALL show the state of the sync: grey on a device without an account; green with a glow, the colour of the lesson's light bulb, on a device with an account while it is online; orange, without the glow, while it is offline. Its accessible name SHALL say the state too: «Синхронизация не включена», «Синхронизация включена», «Синхронизация: нет сети».

#### Scenario: Home page
- **WHEN** a device, with an account or without, opens the home page
- **THEN** the header shows the «Синхронизация» control right after the word mark

#### Scenario: Another screen
- **WHEN** a device opens the words list, a lesson or the themes screen
- **THEN** the header shows the «Синхронизация» control right after the word mark, where it stood on home

#### Scenario: No account
- **WHEN** a device without an account opens the home page
- **THEN** the «Синхронизация» control is grey

#### Scenario: Online
- **WHEN** a device with an account is online
- **THEN** the «Синхронизация» control is green with a glow

#### Scenario: Offline
- **WHEN** a device with an account goes offline
- **THEN** the «Синхронизация» control is orange without a glow
- **AND** it is green with a glow again once the device is back online
