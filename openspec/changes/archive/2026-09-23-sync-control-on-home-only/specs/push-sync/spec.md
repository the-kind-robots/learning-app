## ADDED Requirements

### Requirement: The device-sync control is on the home page only
The system SHALL offer the device-sync control, named «Синхронизация», in the header of the home page only, and only once the device has an account. The control SHALL draw a laptop beside a phone. Every other page SHALL render no such control.

#### Scenario: Home page with an account
- **WHEN** a device with an account opens the home page
- **THEN** the header shows the «Синхронизация» control

#### Scenario: Another page with an account
- **WHEN** a device with an account opens the words list, a lesson or the themes screen
- **THEN** the header shows no «Синхронизация» control

#### Scenario: No account
- **WHEN** a device without an account opens the home page
- **THEN** the header shows no «Синхронизация» control
