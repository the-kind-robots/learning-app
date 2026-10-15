## ADDED Requirements

### Requirement: A long example stays whole while the keyboard is open

While the on-screen keyboard is open the lesson SHALL show the whole prompt: the answer area SHALL start at two rows (the field still grows with its text), the prompt SHALL be set at 18px, the instruction line SHALL be hidden, and no prompt text SHALL be clipped by the lesson body.

#### Scenario: Long prompt, keyboard open

- **WHEN** a long example is on screen and the keyboard opens
- **THEN** the instruction line is hidden
- **AND** the prompt is fully visible above the answer field
- **AND** the answer field is two rows high
