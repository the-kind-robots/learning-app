## MODIFIED Requirements

### Requirement: The device-sync control is on the home page only
The system SHALL offer the device-sync control, named «Синхронизация», in the header of the home page only, with or without an account, once the learner's data is loaded. The control SHALL draw a laptop beside a phone and SHALL stand right after the word mark, centred on its line; it SHALL take a tap within a 44 px square around it without growing. Every other page, and a home page whose data could not be read, SHALL render no such control.

The control SHALL show the state of the sync: grey on a device without an account; green with a glow, the colour of the lesson's light bulb, on a device with an account while it is online; orange, without the glow, while it is offline. Its accessible name SHALL say the state too: «Синхронизация не включена», «Синхронизация включена», «Синхронизация: нет сети».

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
- **THEN** the «Синхронизация» control is orange without a glow
- **AND** it is green with a glow again once the device is back online

## ADDED Requirements

### Requirement: A device without an account asks to turn the sync on
The sync menu of a device without an account SHALL offer «Сохранить мои слова». Activating it SHALL close the menu and open a form, «Чтобы слова не потерялись», that asks, warmly, for a few words and says the icon beside the word mark turns green once it is ready, with one empty field of at most 500 characters, «ОТПРАВИТЬ» and «Отмена». Opening the form SHALL NOT put the focus in the field. «ОТПРАВИТЬ» SHALL stay disabled while the field holds nothing but spaces.

Sending SHALL store the request on the device, close the form once it is stored, and send it (#689): the note, trimmed, with how many words the learner has and on how many days they practised, under the hash of a secret the device makes for the request and keeps. A request that could not be stored SHALL leave the form open, the note in it. While a request is stored and the device has no account, the device SHALL ask what became of it at start and then every minute the page is shown, one asking at a time however often the form is sent: a granted one SHALL open the account at once, without a reload, the control and the menu SHALL show it and examples SHALL be fetched for the learner's words; one the server does not have, or let expire, SHALL be sent again under the same secret, after the wait the server asks for but no sooner than a minute and no later than an hour; a declined one, or one the server refuses outright, SHALL be forgotten, so the next request takes a new secret. A device with no stored request SHALL ask the server nothing.

#### Scenario: Menu without an account
- **WHEN** a device without an account opens the sync menu
- **THEN** the menu offers «Сохранить мои слова»

#### Scenario: Request sent
- **WHEN** the learner writes a few words in the form and sends them
- **THEN** the form closes
- **AND** the server receives the trimmed note, the word count, the days of practice and the hash of the device's secret

#### Scenario: Nothing written
- **WHEN** the field is empty or holds nothing but spaces
- **THEN** «ОТПРАВИТЬ» is disabled

#### Scenario: Cancelled
- **WHEN** the learner presses «Отмена»
- **THEN** the form closes and the menu does not come back

#### Scenario: Request granted
- **WHEN** the server grants the device's request while the app is open
- **THEN** within a minute the «Синхронизация» control turns green, with no reload
- **AND** the sync menu offers «Подключить устройство»

#### Scenario: Request declined, then asked again
- **WHEN** the server declines the device's request and the learner sends the form again
- **THEN** the server receives a request under a new hash
