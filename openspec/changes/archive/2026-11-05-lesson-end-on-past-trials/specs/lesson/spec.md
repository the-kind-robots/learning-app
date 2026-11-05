## MODIFIED Requirements

### Requirement: The learner looks back at the trials already answered
The lesson SHALL keep, in the order they were answered, the trials it has left behind — one entry for each trial answered right, never one for a wrong attempt — each with the number of attempts it took. The lesson screen SHALL lay its trials out as a horizontal strip: the trials left behind, then the current one. The learner SHALL go back one trial by swiping right, and on a screen with a fine pointer also by an arrow beside the progress bar; the strip SHALL stop on each trial. Only the challenge SHALL move with the strip: the bottom panel and its button SHALL stay in place, at the height of the current trial's panel. On a trial left behind the panel SHALL be green and show "Правильно!" when it took one attempt, otherwise "Попыток: N", and the correct answer with the hinted words of an example tappable; its button "Далее" SHALL return to the current trial. Once the last trial is answered the strip SHALL still go back, and a past trial's panel SHALL offer what the final trial's does: «ЕЩЁ УРОК» as its main button, starting a new lesson, and «ЗАКОНЧИТЬ» as a quiet button on the line of its verdict, finishing the lesson.

#### Scenario: Swiping back to a trial answered right at the first try
- **WHEN** the learner has answered a trial right and moved on, and swipes back to it
- **THEN** its prompt is shown, and the panel shows "Правильно!" and its correct answer
- **AND** the progress bar shows the progress before that trial was answered

#### Scenario: A trial answered wrong, then right
- **WHEN** the learner answers a trial wrong, later answers it right, moves on and goes back
- **THEN** the strip has one page for that trial, and its panel shows "Попыток: 2"

#### Scenario: Returning to the current trial
- **WHEN** the learner is on a trial left behind and presses "Далее"
- **THEN** the strip returns to the current trial, and its panel is as it was

#### Scenario: Going back while typing
- **WHEN** the learner has typed part of an answer, goes back to a trial left behind and returns
- **THEN** the typed text is still in the answer field

#### Scenario: Going back on desktop
- **WHEN** the learner on desktop presses the arrow beside the progress bar
- **THEN** the strip goes back one trial

#### Scenario: After the last trial
- **WHEN** the learner has answered the last trial right and goes back to a trial before it
- **THEN** its panel shows its answer, «ЕЩЁ УРОК» and «ЗАКОНЧИТЬ»
- **AND** «ЗАКОНЧИТЬ» finishes the lesson

#### Scenario: One more lesson from a past trial
- **WHEN** the learner has answered the last trial right, goes back to a trial before it and activates «ЕЩЁ УРОК»
- **THEN** the first trial of a new lesson is on display
