## MODIFIED Requirements

### Requirement: The lesson opens and closes by fading through

On a phone, opening or closing the lesson SHALL fade the screen being left out and fade the next one in over 200 ms, with no sideways motion: the lesson fades in from slightly larger, and fades out growing slightly larger when it closes. A screen opened over the lesson, and taken off it, SHALL slide as any other screen does (Requirement: Going deeper slides the new screen in from the right; Requirement: Going back slides the screen out to the right).

#### Scenario: Starting a lesson

- **WHEN** the user taps «НАЧАТЬ УРОК» on a phone
- **THEN** home fades out and the lesson fades in from slightly larger

#### Scenario: Closing a lesson

- **WHEN** the user closes the lesson with ✕ on a phone
- **THEN** the lesson fades out and home fades in

#### Scenario: The statistics over a lesson

- **WHEN** the user taps today's stars in a lesson on a phone, then closes the statistics with ✕
- **THEN** the statistics slide in from the right over the lesson, and slide out to the right as the lesson comes back from the left
