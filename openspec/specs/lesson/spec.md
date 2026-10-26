# lesson Specification

## Purpose
The lesson spec defines the behavior and state management for interactive learning sessions where users practice vocabulary through trials. It covers lesson initialization, trial progression, and configuration options for customizing the learning experience.

## Requirements

### Requirement: Lesson state includes options object
The system SHALL create lesson state with an options object containing configuration settings.

#### Scenario: Lesson initialization with options
- **WHEN** creating initial lesson state
- **THEN** the state includes an `options` object with `trial-selector`
- **AND** `trial-selector` defaults to `:random` if not specified

### Requirement: Lesson advancement uses options
The system SHALL use trial-selector from lesson options when advancing to next trials.

#### Scenario: Trial advancement with options
- **WHEN** advancing to the next trial
- **THEN** the selection uses the `trial-selector` from lesson `options`
- **AND** maintains backward compatibility for lessons without options

### Requirement: Lesson answer checks update lesson state
The system SHALL return updated lesson state after answer checks, recording the result in `:last-result`.

#### Scenario: Answer check response
- **WHEN** an answer is checked
- **THEN** the response includes `lesson-state` with `:last-result` containing `:correct?` and `:answer`

### Requirement: Lesson trial generation rules
The system SHALL generate trials for each word, each phrase, and each example, using denormalized prompts and answers. A word or phrase SHALL produce at most one example trial: of the examples the lesson's collection sees for it, the one with the smallest id, so the same one every time. The other examples stay stored; a lesson does not show them until it can show several. A phrase SHALL produce example trials on the same terms as a word. A lesson on a parent collection SHALL see, for each word, the examples made for the parent when there are any, otherwise those made for its children.

#### Scenario: Trial generation
- **WHEN** a lesson starts
- **THEN** each word produces a word trial and each word with examples produces one example trial
- **AND** example trials start in a locked state until the vocabulary trial they belong to is answered correctly

#### Scenario: A word with several examples
- **WHEN** a lesson starts with a word that has two examples the lesson's collection sees
- **THEN** the word produces one example trial, from the example with the smaller id

#### Scenario: A word that lies only in a child
- **WHEN** a lesson starts on the parent `Reisen` with a word that lies only in its child `Reisen/Bahn`, which has an example of it
- **THEN** the word produces one example trial, from the child's example

#### Scenario: A word with an example in the parent and in a child
- **WHEN** a lesson starts on a parent with a word that has an example made for the parent and one made for a child
- **THEN** the example trial is the parent's

#### Scenario: Phrase trial generation
- **WHEN** a lesson starts with a phrase among the selected items
- **THEN** the phrase produces a single phrase trial with the translation as prompt and the phrase text as answer
- **AND** an example stored for that phrase produces one example trial, locked until the phrase trial is answered correctly

### Requirement: Lesson state is denormalized
The system SHALL hold lesson state without a separate `:words` collection.

#### Scenario: Lesson state fields
- **WHEN** a lesson starts
- **THEN** its state includes `:trials`, `:remaining-trials`, `:current-trial`, and `:last-result`
- **AND** it omits a `:words` field

### Requirement: Lesson entry starts a fresh session
The system SHALL start a new lesson session every time the user enters the lesson flow from the UI, drawn from the current vocabulary state.

#### Scenario: Re-entering lesson after leaving an unfinished session
- **WHEN** a user enters the lesson flow after leaving an unfinished session
- **THEN** the system creates a new lesson session from the current vocabulary state
- **AND** the newly rendered lesson does not continue the one that was left

### Requirement: A lesson in progress is not stored
The lesson in progress SHALL be held by the open app only and SHALL NOT be written to a database. What its answers record — the reviews — is written as before.

#### Scenario: Answering a lesson
- **WHEN** a learner starts a lesson and answers its trials
- **THEN** no lesson document is written to any local database
- **AND** a review is recorded for each checked word-trial answer

### Requirement: Word-trial answers record review progress
The system SHALL record review data for word-trial answers so retention-based progress updates are reflected after lesson activity.

#### Scenario: Answering a word trial
- **WHEN** a user submits an answer for a word trial
- **THEN** the system records a review for that word using the answer result
- **AND** subsequent progress calculations can observe the updated review data

#### Scenario: Answering an example trial
- **WHEN** a user submits an answer for an example trial
- **THEN** the system does not create a vocabulary review document for that answer

### Requirement: Phrase-trial answers record review progress
Graded phrase-trial answers SHALL write a review document on every graded attempt, as word trials do. A trial answered wrongly stays in the pool and may be graded again in the same lesson; each attempt is its own review.

#### Scenario: Both attempts are written
- **WHEN** a phrase trial is answered incorrectly and later correctly in the same lesson
- **THEN** a review with `retained` false and a review with `retained` true are written for that trial

### Requirement: Lesson trial unlocking controls example visibility
The system SHALL keep example trials hidden from lesson selection until the vocabulary trial carrying the same `word-id` has been answered correctly. That trial is a word trial for a word and a phrase trial for a phrase; the kind of the entry SHALL NOT change whether the lock applies.

#### Scenario: Lesson start selectable pool
- **WHEN** a lesson starts
- **THEN** the selectable trial pool contains only unlocked word and phrase trials
- **AND** locked example trials still count as lesson trials for progress tracking

#### Scenario: Successful word answer unlocks examples
- **WHEN** a user answers a word trial correctly
- **THEN** example trials with the same `word-id` become unlocked
- **AND** those unlocked example trials join the normal selectable pool
- **AND** the next selected trial is still chosen by the lesson trial-selector rather than forced to that example

#### Scenario: Successful phrase answer unlocks examples
- **WHEN** a user answers a phrase trial correctly
- **THEN** example trials with the same `word-id` become unlocked and join the normal selectable pool
- **AND** an incorrect phrase answer leaves them locked

### Requirement: Example answer rendering keeps structure annotations
The system SHALL keep example structure data on lesson example trials so the correct answer can be rendered with deterministic word annotations.

#### Scenario: Example trial stores answer structure
- **WHEN** an example trial is generated for a lesson
- **THEN** it keeps the example `structure` items alongside the denormalized prompt and answer

#### Scenario: Correct example answer is segmented by structure
- **WHEN** lesson UI renders the correct answer for an example trial
- **THEN** it builds answer segments from the German answer text plus each structure item `wordIndex`
- **AND** annotated segments keep the matching `dictionaryForm` and `translation`

### Requirement: Error reveal labels the correct answer with short copy
The lesson error footer SHALL label the revealed correct answer with the header «Правильно:».

#### Scenario: Wrong answer reveal
- **WHEN** a user answers a trial incorrectly
- **THEN** the footer shows the user's answer under «Ваш ответ:»
- **AND** the correct answer under «Правильно:»

### Requirement: Answer field suppresses text assistance and wraps long input
The lesson answer field SHALL be a multi-line textarea with browser text assistance disabled — `spellcheck="false"`, `autocorrect="off"`, `autocapitalize="none"`, `autocomplete="off"` — so German input is not corrected against the user, and long answers SHALL wrap onto new lines inside the field.

#### Scenario: Text assistance attributes present
- **WHEN** the lesson input footer renders
- **THEN** the answer field is a textarea with spellcheck, autocorrect, autocapitalize and autocomplete disabled

#### Scenario: Long answer wraps
- **WHEN** the typed answer exceeds the field width
- **THEN** the text soft-wraps onto further lines within the textarea

### Requirement: Answer token click opens token-info card

The lesson UI SHALL open a token-info card when the user activates an annotated word in the revealed correct answer. The card SHALL render as a popover anchored to the activated word — not as a centered modal — with an arrow pointing at the word, and SHALL reflect the word's vocabulary state, offering the matching action.

#### Scenario: Unknown word

- **WHEN** the user clicks an annotated word whose dictionary form is not in the vocabulary
- **THEN** a popover opens anchored above the word (below when there is no room above), showing the dictionary form and translation
- **AND** the popover offers an add-to-dictionary action that saves the word with the translation

#### Scenario: Known word missing this translation

- **WHEN** the user clicks an annotated word that exists in the vocabulary without this translation
- **THEN** the popover offers an add-translation action that merges the translation into the word

#### Scenario: Known word with translation

- **WHEN** the user clicks an annotated word already stored with this translation
- **THEN** the popover shows the word as already in the dictionary and dismisses itself after a short delay

#### Scenario: Dismissal

- **WHEN** the popover is open
- **THEN** clicking outside it or pressing Escape closes it
- **AND** on hover-capable devices it auto-closes after an idle timeout unless the pointer or focus is inside it
- **AND** no modal chrome, backdrop dimming, or forced focus ring is presented

### Requirement: Revealed answer is selectable as continuous text

The revealed correct answer SHALL be selectable with the mouse as one continuous text run, across plain and hinted words alike, so it can be copied whole. Hinted words SHALL remain activatable.

#### Scenario: Selecting across hinted words

- **WHEN** the user drags a selection across the revealed answer
- **THEN** the selection includes the hinted words' text
- **AND** the copied text matches the visible sentence

#### Scenario: Hinted word still opens the hint

- **WHEN** the user clicks a hinted word without dragging
- **THEN** the hint card opens as before

### Requirement: Leaving a lesson returns home
The system SHALL show the home screen when a learner leaves a lesson — by finishing it, by closing it, or by the browser's or the system's Back — and leaving SHALL end the lesson, whichever way out was taken. Pressing the browser's Back on home SHALL NOT restore the lesson.

#### Scenario: Finished lesson exit
- **WHEN** a learner completes the final lesson trial and exits the lesson flow
- **THEN** the home screen is on display
- **AND** pressing the browser's Back does not show the lesson

#### Scenario: Cancelled lesson exit
- **WHEN** a learner closes an active lesson from the corner
- **THEN** the home screen is on display
- **AND** pressing the browser's Back does not show the lesson

#### Scenario: Leaving a lesson by Back
- **WHEN** a learner who has answered part of a lesson presses the browser's Back
- **THEN** the home screen is on display
- **AND** entering the lesson again starts a fresh session

### Requirement: Hinted words take the width of plain text
The revealed correct answer SHALL occupy the same width whether or not its words carry hints: a hinted word SHALL add no width of its own, at rest, on hover, on focus or while its hint is open.

#### Scenario: Sentence with hinted words
- **WHEN** the revealed answer shows a sentence with hinted words
- **THEN** its rendered width equals that of the same text without hints, within half a pixel

### Requirement: Continuing a lesson advances one trial
Activating the continue button («ДАЛЕЕ» or «ЗАКОНЧИТЬ») once SHALL act once: one press of Enter on the focused button, or one click, advances the lesson by exactly one trial. A double click SHALL advance it by one trial as well.

#### Scenario: Enter on the continue button
- **WHEN** the continue button is focused after an answer and the user presses Enter once
- **THEN** the lesson shows the next trial, skipping none

#### Scenario: Double click on the continue button
- **WHEN** the user double-clicks the continue button
- **THEN** the lesson advances by one trial

### Requirement: A blank answer is graded as not recalled
Checking an answer that is empty or only whitespace SHALL grade it as not recalled, as any wrong answer is, on every trial type: the correct answer is revealed and the trial stays in the lesson. A word or phrase trial SHALL record a review as not retained; an example trial records none, as for a wrong answer. The revealed result SHALL NOT show an answer line for the learner when nothing was typed.

#### Scenario: Checking with nothing typed
- **WHEN** the answer field is empty and the user activates «ПРОВЕРИТЬ»
- **THEN** the correct answer is revealed and «ДАЛЕЕ» continues the lesson
- **AND** a review is recorded as not retained

### Requirement: Tiles are placed, returned and reordered

Activating a bank tile SHALL append it to the tray; activating a placed tile SHALL return it to the bank, and the bank SHALL close ranks with no empty places. A tile SHALL be draggable: it follows the pointer, a caret marks where it will land, and releasing it there puts it there, reordering the tray for a placed tile and inserting it into the tray for a bank tile. A bank tile released outside the tray SHALL stay in the bank.

#### Scenario: Placed tile returned

- **WHEN** the learner activates a placed tile
- **THEN** it leaves the tray and is back in the bank

#### Scenario: Placed tile dragged

- **WHEN** the learner drags a placed tile to another place in the tray and releases it
- **THEN** the tray's order changes to put it there

#### Scenario: Bank tile dragged into the tray

- **WHEN** the learner drags a bank tile into the tray and releases it at a caret between placed tiles
- **THEN** the tile is in the tray at the caret's place and has left the bank

#### Scenario: Bank tile released outside the tray

- **WHEN** the learner drags a bank tile and releases it outside the tray
- **THEN** the tile stays in the bank and the tray is unchanged

### Requirement: A tile answer is checked like a typed one

The check button SHALL be available whatever the number of placed tiles. The answer SHALL be the tiles' texts in tray order, separated by spaces, and SHALL be graded exactly as a typed answer: word order is strict.

#### Scenario: Tiles left in the bank

- **WHEN** at least one tile is still in the bank and the learner checks
- **THEN** the placed tiles are graded as the answer, and shown as wrong when they are not the whole sentence
- **AND** with no tile placed the answer is graded as not recalled

#### Scenario: Tiles in the right order

- **WHEN** every tile is placed in the sentence's order and the learner checks
- **THEN** the lesson shows «Правильно!»

### Requirement: A lesson takes the most due words
Words and phrases SHALL be selected into the lesson from the most due vocabulary in the active scope: the lesson takes the most due items there are, up to its size.

How due an item is SHALL be measured so that the measure keeps separating items however long they have gone unreviewed: the elapsed time since the last review, counted in that item's forgetting time-constants. An item never reviewed SHALL be the most due there is.

An item that is strictly more due than another SHALL be preferred to it every time. Where more items are equally due than the lesson has room for, which of them the lesson takes SHALL be decided at random, and SHALL NOT follow the order the vocabulary is stored or listed in. A vocabulary smaller than the lesson SHALL be taken whole.

#### Scenario: Two lessons over equally due vocabulary differ
- **WHEN** lessons start repeatedly over a vocabulary larger than one lesson where every item is equally due
- **THEN** they do not all serve the same items
- **AND** they are not the ones the vocabulary happens to be listed first

#### Scenario: Long-unreviewed items still rank against each other
- **WHEN** two items were last reviewed five days and thirty days ago
- **THEN** the item last reviewed thirty days ago is the more due of the two

#### Scenario: A lesson takes the most due, not a draw from them
- **WHEN** the vocabulary holds a word just added and more long-forgotten words than one lesson takes
- **THEN** every lesson serves the long-forgotten words and never the new one

#### Scenario: A more due item beats a tied field
- **WHEN** one item is strictly more due than a field of equally due items larger than the lesson
- **THEN** the more due item is in the lesson every time

### Requirement: Tiles are placed by typing

While tiles are shown, letters typed on a physical keyboard SHALL accumulate as a prefix, compared without regard to case and as if the words had no umlauts: ä, ö, ü SHALL match a, o, u, and ß SHALL match ss. While a prefix is pending, every bank tile whose word starts with it SHALL show those leading letters in a heavier weight, and no tile SHALL change its size; the highlight SHALL clear with the prefix. As soon as the prefix fits exactly one distinct word among the bank's tiles, the first tile of that word in bank order SHALL move to the end of the tray and the prefix SHALL clear; tiles of the same text count as one word. Space and Enter SHALL act alike: when the pending prefix identifies a bank word, either as the only distinct word it fits or as a whole bank word it spells (such as «die» beside «dieser»), the first tile of that word in bank order SHALL move to the end of the tray; when it identifies none, they SHALL do nothing and the prefix SHALL stay. A letter that would make the prefix fit no bank word SHALL be ignored. Backspace SHALL erase the prefix's last letter, or with no prefix return the last placed tile to the bank. Typing SHALL NOT open an on-screen keyboard, and Enter with no prefix SHALL NOT place or check anything.

#### Scenario: A unique prefix places its tile

- **WHEN** the bank holds «Der», «Hund», «schläft» and the learner types «h»
- **THEN** «Hund» moves to the end of the tray and no prefix is pending

#### Scenario: Umlauts typed without them

- **WHEN** the bank holds «über» and «groß» beside words that start otherwise
- **THEN** typing «u» places «über»
- **AND** typing «g», «r», «o», «s», «s» places «groß»

#### Scenario: A pending prefix is highlighted on the tiles

- **WHEN** the bank holds «die» and «dieser» and the learner types «d», «i»
- **THEN** the letters «di» are heavier on both tiles and the tiles keep their widths
- **AND** the highlight is gone once the prefix is cleared or the tile is placed

#### Scenario: A whole word that starts another waits for Space or Enter

- **WHEN** the bank holds «die» and «dieser» and the learner types «d», «i», «e»
- **THEN** nothing is placed and «die» stays highlighted
- **AND** pressing Space or Enter moves «die» to the end of the tray

#### Scenario: An ambiguous prefix that is no whole word

- **WHEN** the bank holds «die» and «dieser», the prefix «di» is pending and the learner presses Space or Enter
- **THEN** nothing is placed and the prefix stays

#### Scenario: Duplicate words

- **WHEN** the bank holds two tiles «der» and the learner types «d», «e», «r»
- **THEN** the first «der» in bank order moves to the tray and the other stays in the bank

#### Scenario: A prefix fitting no word is ignored

- **WHEN** the learner types a letter that starts no bank word
- **THEN** the letter is not accepted and the prefix is unchanged

#### Scenario: Backspace

- **WHEN** a prefix is pending and the learner presses Backspace
- **THEN** its last letter is erased
- **AND** with no prefix pending, Backspace returns the last placed tile to the bank

### Requirement: A lesson opened on a phone does not raise the keyboard
On a device without a fine pointer, opening a lesson SHALL NOT focus the answer field, every time, including the first lesson after the app starts. On a device with a fine pointer the answer field SHALL receive focus when the lesson opens.

#### Scenario: First lesson after a cold start on a phone
- **WHEN** the app has just started on a phone and the learner starts a lesson
- **THEN** the answer field is not focused

### Requirement: An example is answered with tiles

Every showing of an example SHALL show, in place of the plain field, a tray and a bank of the answer's words as shuffled tiles, with no light bulb; the showing SHALL NOT offer the plain field. Tiles SHALL keep the sentence's case and carry no punctuation at a word's edges; a symbol written with the word — a currency sign, a percent sign — SHALL stay on its tile, and a symbol written apart SHALL share the tile of the word before it. A repeated word SHALL be a separate tile. Word and phrase trials SHALL show the plain field and no light bulb.

#### Scenario: Example shown

- **WHEN** an example is shown in the lesson
- **THEN** the answer field is an empty tray and a bank holding every word of the answer as a tile
- **AND** there is no light bulb and no plain field

#### Scenario: Amount with its sign

- **WHEN** an example whose answer holds «1000€» and «50%» is shown in the lesson
- **THEN** the bank holds the tiles «1000€» and «50%», and no tile ends in the sentence's full stop

#### Scenario: Amount with its sign written apart

- **WHEN** an example whose answer holds «1000 €» and «50 %» is shown in the lesson
- **THEN** the bank holds the tiles «1000 €» and «50 %», and no tile holds a sign alone

#### Scenario: Word shown

- **WHEN** a word or a phrase is to be translated
- **THEN** the plain field is shown, with no tiles and no light bulb

### Requirement: Enter in the answer field checks the answer
While the answer field shows the textarea, Enter without Alt — including the Done key of a phone's on-screen keyboard — SHALL check the answer, as the ПРОВЕРИТЬ button does, and SHALL NOT insert a line break. Enter during text composition SHALL only end the composition.

#### Scenario: Enter after typing
- **WHEN** the learner types an answer and presses Enter
- **THEN** the answer is checked
- **AND** no line break was added to it
