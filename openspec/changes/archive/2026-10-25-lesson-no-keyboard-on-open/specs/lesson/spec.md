## ADDED Requirements

### Requirement: A lesson opened on a phone does not raise the keyboard
On a device without a fine pointer, opening a lesson SHALL NOT focus the answer field, every time, including the first lesson after the app starts. On a device with a fine pointer the answer field SHALL receive focus when the lesson opens.

#### Scenario: First lesson after a cold start on a phone
- **WHEN** the app has just started on a phone and the learner starts a lesson
- **THEN** the answer field is not focused
