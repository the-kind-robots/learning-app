## MODIFIED Requirements

### Requirement: The statistics can be shared as a picture
«ПОДЕЛИТЬСЯ» at the foot of the statistics screen SHALL share a PNG picture, 1080 by 1350 pixels, of the words' number and histogram and the lessons' number and their last fourteen days, each with its count over it on a day with lessons and its day of the month and weekday under it, through the system's share sheet. Where the browser offers no share sheet for files, the picture SHALL be downloaded. The picture SHALL be drawn in the colours of the scheme the app shows: on the ground `#150f1c` with light text in the dark scheme, on white in the light one.

#### Scenario: Sharing
- **WHEN** the learner taps «ПОДЕЛИТЬСЯ»
- **THEN** the share sheet is offered one PNG file of 1080 by 1350 pixels

#### Scenario: No share sheet
- **WHEN** the learner taps «ПОДЕЛИТЬСЯ» in a browser with no share sheet for files
- **THEN** a PNG file is downloaded

#### Scenario: Sharing at night
- **WHEN** the app shows its dark scheme and the learner taps «ПОДЕЛИТЬСЯ»
- **THEN** the picture's ground is `#150f1c` and its text is light
