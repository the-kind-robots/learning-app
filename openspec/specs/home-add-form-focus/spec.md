# home-add-form-focus Specification

## Purpose
TBD - created by archiving change restore-home-add-form-focus. Update Purpose after archive.
## Requirements
### Requirement: Successful desktop add-word submit restores primary input focus
The system SHALL restore focus to the German word input after a successful home add-word submit on desktop-style pointer devices.

#### Scenario: Desktop repeated entry stays in the German input
- **WHEN** a user successfully submits the home add-word form on a desktop-style pointer device
- **THEN** the form is reset through the normal success flow
- **AND** focus returns to the `#new-word-value` input
- **AND** the focus restoration is implemented as a Replicant lifecycle hook, not an htmx event handler

### Requirement: Mobile submit does not force focus restoration
The system SHALL avoid forcing focus restoration on touch-first/mobile devices after a successful home add-word submit.

#### Scenario: Mobile success does not reopen the keyboard
- **WHEN** a user successfully submits the home add-word form on a coarse-pointer or touch-first device
- **THEN** the success flow completes without explicitly focusing the German input
- **AND** the implementation does not intentionally reopen the soft keyboard

