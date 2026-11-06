## MODIFIED Requirements

### Requirement: The learner looks back at the trials already answered
The lesson SHALL keep, in the order they were answered, the trials it has left behind — one entry for each trial answered right, never one for a wrong attempt — each with the number of attempts it took. The lesson screen SHALL lay its trials out as a horizontal strip: the trials left behind, then the current one. The learner SHALL go back one trial by swiping right, or by scrolling sideways with a wheel or a trackpad; the strip SHALL stop on each trial. No arrow SHALL stand beside the progress bar. On a keyboard `ArrowLeft` SHALL show the trial before the one shown and `ArrowRight` the one after it, stopping at the first trial and at the current one; while the current trial is shown waiting for its answer, the arrows SHALL stay with the answer field. While a trial left behind is shown, `Enter` and `Ctrl`+`Enter` SHALL press its main button. Only the challenge SHALL move with the strip: the current trial's text SHALL keep its place, hidden while a trial left behind is shown, and that trial's answer SHALL lie over it along the bottom at its own height, above the main button. The panel SHALL have one main button whichever trial is shown, and paging SHALL keep the focus on it; back on a current trial waiting for its answer, the focus SHALL leave it, so a second `Enter` does not check an empty answer. When the trial shown changes, the panel's edge SHALL travel to the new height smoothly, never at once, while the text stays where it stands: the text shown SHALL fade first, and the new text SHALL appear in its place once the edge has nearly arrived, never cut by the edge; the button SHALL neither move nor fade. With reduced motion asked for, the change SHALL be instant. On a trial left behind the panel SHALL be green and show "Правильно!" when it took one attempt, otherwise "Попыток: N", and the correct answer with the hinted words of an example tappable; the main button "Далее" SHALL return to the current trial. Once the last trial is answered the strip SHALL still go back, and a past trial's panel SHALL offer what the final trial's does: «ЕЩЁ УРОК» as its main button, starting a new lesson, and «ЗАКОНЧИТЬ» as a quiet button on the line of its verdict, finishing the lesson; while the trial shown changes, «ЗАКОНЧИТЬ» SHALL keep its place on that line, travelling with the panel's edge, never fading.

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
- **WHEN** the learner on desktop turns the wheel sideways over the strip
- **THEN** the strip goes back one trial
- **AND** no arrow stands beside the progress bar

#### Scenario: Paging with the arrow keys
- **WHEN** the learner has answered a trial and the current one, and presses `ArrowLeft`
- **THEN** the trial before is shown, and one more `ArrowLeft` keeps it
- **AND** `ArrowRight` shows the current trial again

#### Scenario: The arrows stay with the answer field
- **WHEN** the current trial waits for an answer typed in its field and the learner presses `ArrowLeft`
- **THEN** the current trial stays shown

#### Scenario: Enter returns from a trial left behind
- **WHEN** the learner on a trial left behind presses `Enter`, or `Ctrl`+`Enter`
- **THEN** the current trial is shown, as "Далее" would show it

#### Scenario: After the last trial
- **WHEN** the learner has answered the last trial right and goes back to a trial before it
- **THEN** its panel shows its answer, «ЕЩЁ УРОК» and «ЗАКОНЧИТЬ»
- **AND** «ЗАКОНЧИТЬ» finishes the lesson

#### Scenario: One more lesson from a past trial
- **WHEN** the learner has answered the last trial right, goes back to a trial before it and activates «ЕЩЁ УРОК»
- **THEN** the first trial of a new lesson is on display

#### Scenario: The panel changes height smoothly
- **WHEN** the learner goes back to a trial whose panel is of another height than the one shown
- **THEN** the panel's edge passes through the heights between the two, then stands at the new one
- **AND** the button stays where it was

#### Scenario: The text stays in place
- **WHEN** the trial shown changes
- **THEN** the text shown fades where it stands, and the new text appears where it stands once the edge has nearly arrived

#### Scenario: The way home travels with the edge
- **WHEN** the lesson is over and the learner goes back to a trial whose panel is of another height
- **THEN** «ЗАКОНЧИТЬ» stays on the line of the verdict, moving with the panel's edge, and shows all the while

### Requirement: The end of a lesson offers one more
When the final trial of a lesson is answered correctly, the footer SHALL offer «ЕЩЁ УРОК» as its main button, focused, standing exactly where «ДАЛЕЕ» stands after any other trial, and «ЗАКОНЧИТЬ» as a quiet button on the line of «Правильно!», at its end. «ЕЩЁ УРОК» SHALL start a new lesson at once, on the lesson screen. «ЗАКОНЧИТЬ» SHALL show the home screen. From then on, on whichever trial is shown, `Escape` SHALL do what «ЗАКОНЧИТЬ» does, unless a word's card, a menu or a dialog is open over the lesson: then `Escape` closes only that. Before the final trial is answered `Escape` SHALL leave the lesson as it is.

#### Scenario: The main button does not move
- **WHEN** a learner answers the last but one trial and then the final trial correctly, with answers of the same length
- **THEN** «ЕЩЁ УРОК» occupies the same place on screen that «ДАЛЕЕ» did

#### Scenario: One more lesson
- **WHEN** a learner answers the final trial correctly and activates «ЕЩЁ УРОК»
- **THEN** the first trial of a new lesson is on display
- **AND** the trials of the finished lesson are gone from the strip

#### Scenario: Finishing after the last trial
- **WHEN** a learner answers the final trial correctly and activates «ЗАКОНЧИТЬ»
- **THEN** the home screen is on display

#### Scenario: Escape after the last trial
- **WHEN** a learner answers the final trial correctly and presses `Escape`
- **THEN** the home screen is on display

#### Scenario: Escape over a word's card or a dialog after the last trial
- **WHEN** a learner answers the final trial correctly, opens a word's card or the «Сохранить мои слова» dialog, and presses `Escape`
- **THEN** only the card or the dialog closes, and the lesson stays on display

#### Scenario: Escape mid-lesson
- **WHEN** a learner presses `Escape` before the final trial is answered
- **THEN** the lesson stays on display
