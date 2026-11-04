## ADDED Requirements

### Requirement: The sync menu opens under its control
Pressing the «Синхронизация» control SHALL open the sync menu as a menu under it, drawn like the settings menu, the screen staying as it was behind it; pressing the control again, pressing anywhere outside the menu, or Escape SHALL close it. The menu SHALL have no «Отмена» row. Activating any of its items SHALL close it.

#### Scenario: Opening the menu
- **WHEN** the learner presses the «Синхронизация» control on the home page
- **THEN** the sync menu opens under the control and the home page stays as it was behind it
- **AND** the menu has no «Отмена»

#### Scenario: Closing the menu
- **WHEN** the learner presses outside the open sync menu, presses Escape, or presses the «Синхронизация» control again
- **THEN** the menu closes and the screen is as it was

#### Scenario: An item closes the menu
- **WHEN** the learner activates an item of the sync menu
- **THEN** the menu closes
