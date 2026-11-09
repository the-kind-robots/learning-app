# statistics Specification

## Purpose
What the statistics a tap on today's stars opens show: the words by how well they are remembered, the lessons by day, and the picture they share.

## Requirements

### Requirement: Today's stars open the statistics
Outside a lesson and outside the statistics screen, today's stars in the shell bar SHALL be a button that opens the statistics screen. In a lesson the stars SHALL NOT be a button. The statistics screen SHALL close by ✕ in the corner, by `Escape` and by the browser's Back, to the screen it was opened from (app-navigation, Requirement: The open windows are a stack).

#### Scenario: Opening from home
- **WHEN** a learner with lessons today taps the stars on home
- **THEN** the statistics screen is on display

#### Scenario: Stars in a lesson
- **WHEN** a learner with lessons today is in a lesson
- **THEN** the stars are not a button

#### Scenario: Closing
- **WHEN** the learner presses `Escape` on the statistics screen
- **THEN** home is on display

### Requirement: The statistics show the words by memory
The statistics screen SHALL first show the number of words in the vocabulary, then a histogram of them by the percent the word list shows for each: ten columns, 0–10% to 90–100%, each in the word list's colour of its middle at half opacity, as high as its share of the tallest, with its count of words in it. A word never answered counts at 0%. Under the histogram one strip SHALL be split by count into the four groups of the word list — new (below 20%), due (20–50%), good (50–80%), excellent (80% and up) — and each group's count SHALL be shown with its name.

#### Scenario: Words never answered
- **WHEN** a learner whose two words were never answered opens the statistics
- **THEN** the words' number reads 2, the 0–10% column counts 2, and «новые» reads 2

### Requirement: The statistics show the lessons by day
Under the words the screen SHALL show the number of lessons passed, then a column for every day from the day of the first lesson to today — to the day of the last lesson when a device's clock ran ahead, today alone with no lesson — as high as its lessons, half-opaque gold, purple from five lessons on, with the count over it on a day with lessons. Under each column SHALL be its day of the month and its weekday; today's date SHALL be marked. The name of the month SHALL stand under its first day and SHALL stay at the left edge while its days are in view. The row SHALL scroll sideways and SHALL open scrolled to today.

#### Scenario: Lessons on three days
- **WHEN** a learner passed two lessons today, none yesterday and five the day before, and opens the statistics
- **THEN** the lessons' number reads 7, and there are three columns, the last reading 2 and the first, purple, 5

#### Scenario: A lesson dated tomorrow
- **WHEN** a learner has a lesson today and one dated tomorrow by a device whose clock runs ahead, and opens the statistics
- **THEN** the lessons' number reads 2, there are two columns, and today's is the first

#### Scenario: Opened on today
- **WHEN** a learner whose first lesson was sixty days ago opens the statistics on a phone
- **THEN** today's column is in view at the right

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
