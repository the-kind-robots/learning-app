## MODIFIED Requirements

### Requirement: The device-sync control is on the home page only
The system SHALL offer the device-sync control, named «Синхронизация», in the header of the home page only, with or without an account. The control SHALL draw a laptop beside a phone and SHALL stand right after the word mark, centred on its line. Every other page SHALL render no such control.

The control SHALL show the state of the sync: grey on a device without an account; green with a glow, the colour of the lesson's light bulb, on a device with an account while it is online; the same green faded, without the glow, while it is offline.

#### Scenario: Home page
- **WHEN** a device, with an account or without, opens the home page
- **THEN** the header shows the «Синхронизация» control right after the word mark

#### Scenario: Another page with an account
- **WHEN** a device with an account opens the words list, a lesson or the themes screen
- **THEN** the header shows no «Синхронизация» control

#### Scenario: No account
- **WHEN** a device without an account opens the home page
- **THEN** the «Синхронизация» control is grey

#### Scenario: Online
- **WHEN** a device with an account is online
- **THEN** the «Синхронизация» control is green with a glow

#### Scenario: Offline
- **WHEN** a device with an account goes offline
- **THEN** the «Синхронизация» control is faded green without a glow
- **AND** it is green with a glow again once the device is back online

## ADDED Requirements

### Requirement: A device without an account asks for an invite
The sync menu of a device without an account SHALL offer «Сохранить мои слова». Activating it SHALL close the menu and open a form, «Чтобы слова не потерялись», that asks, warmly, for a few words and says the icon beside the word mark turns green once it is ready, with one field, «ОТПРАВИТЬ» and «Отмена». The field SHALL start empty, and an empty field SHALL NOT send. Sending SHALL close the form. Until the invite service exists (#650) the request goes no further than the device.

#### Scenario: Menu without an account
- **WHEN** a device without an account opens the sync menu
- **THEN** the menu offers «Сохранить мои слова»

#### Scenario: Request sent
- **WHEN** the learner writes a few words in the invite form and sends them
- **THEN** the form closes

#### Scenario: Nothing written
- **WHEN** the learner sends the invite form with the field empty
- **THEN** the form stays open
