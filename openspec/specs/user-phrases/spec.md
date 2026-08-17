# user-phrases Specification

## Purpose
User phrases are multi-word German expressions a user authors and learns as a unit. This spec covers how they are entered, how they sit in the vocabulary alongside words, and how a lesson asks for them.
## Requirements

### Requirement: Phrases are added from the home form with automatic mode detection
The home add form SHALL detect whether the input is a word or a phrase without a manual toggle. When an already-fetched completion is the input itself, its pos SHALL decide: `phrase` selects phrase mode, anything else word mode. Otherwise input containing a space (after trim) SHALL be treated as a phrase, EXCEPT when it is an article (der/die/das/ein/eine) or `sich` followed by a single token. Selecting an autocomplete suggestion SHALL set the mode from the suggestion: multi-word pos=phrase suggestions select phrase mode, all others word mode. A selected suggestion's mode SHALL hold only while the entered value is still the selected lemma, compared by the normalization that gives a vocabulary document its id; once the value differs, detection SHALL decide again. The form SHALL offer no control over the mode: the panel legend and the field label are what state it, and a selection is the only thing that can override detection. A multi-word noun is entered as a word by typing it with its article, as the dictionary lists it.

#### Scenario: Space switches to phrase mode
- **WHEN** the user types "auf jeden Fall" into the German field
- **THEN** the form enters phrase mode, and the legend and the field label say so
- **AND** open suggestions are cleared and stale async completion responses do not prefill the translation

#### Scenario: Article plus noun stays a word
- **WHEN** the user types "der Tisch" or "sich freuen"
- **THEN** the form stays in word mode and autocomplete keeps working

#### Scenario: An article decides a multi-word noun
- **WHEN** the user types "das Tiny House"
- **THEN** the form stays in word mode, which is how a multi-word noun is entered

#### Scenario: Typing on from a selected word switches to phrase mode
- **WHEN** the user selects the suggestion "das Haus" and then appends " ist gross"
- **THEN** the form enters phrase mode and the entry is saved as a phrase

#### Scenario: A selected phrase lemma survives until it is edited
- **WHEN** the user selects the pos=phrase suggestion "das heißt" and submits it unchanged
- **THEN** the form is in phrase mode, although the value alone reads as an article pair

#### Scenario: Mode flip preserves input
- **WHEN** the detected mode changes while the user is typing
- **THEN** the entered German text and translation are preserved (inputs are not remounted)

### Requirement: Phrase and translation inputs are multi-line
Both the German input and the translation input SHALL be auto-growing multi-line fields: single-row when compact, growing with content up to a maximum height, then scrolling internally. Auto-growing SHALL work on Safari (JS fallback when `field-sizing: content` is unsupported). Line breaks SHALL be visual only and SHALL be collapsed to single spaces on submit.

#### Scenario: Long phrase grows the field
- **WHEN** the user pastes a phrase longer than one visual line
- **THEN** the field grows to show the content up to the maximum height without horizontal scrolling

### Requirement: Adding a phrase creates a phrase document without example generation
Submitting the form in phrase mode SHALL create a vocabulary document of the `"phrase"` kind with the translation stored as a single entry (never split on punctuation), seed an initial review, and add it to the active collection. No LLM example fetch SHALL be queued for phrases. Re-adding an existing value SHALL merge translations as whole entries and add it to the active collection.

#### Scenario: Phrase with sentence translation survives punctuation
- **WHEN** the user adds "Entschuldigung, dass ich zu spät komme" with translation "Извини, что я опоздал."
- **THEN** one phrase document exists whose translation is the single entered string
- **AND** no example-fetch task is queued

#### Scenario: Duplicate phrase merges
- **WHEN** the user adds a phrase whose normalized value already exists
- **THEN** the translations are merged as whole entries and the document count does not grow

### Requirement: One value is one entry
A value identifies one vocabulary document whatever its kind. Submitting a phrase whose value already exists SHALL merge the translation into that document, without a warning, a confirmation, or a second press, and SHALL leave its kind unchanged.

#### Scenario: Phrase matching an existing word
- **WHEN** the user submits a phrase whose normalized value equals an existing document's value
- **THEN** the translation merges into it, its kind is untouched, and no second document appears

### Requirement: Phrases appear in the words list
The words page SHALL show phrases in the same list as words, sorted by the same retention order, and wrapped over multiple lines instead of truncated. Search and word counts SHALL include phrases. No type label is shown: the list reads as one vocabulary, and how a row is laid out is what tells the two apart. The type decision SHALL be computed by the presenter, not the view.

#### Scenario: Phrase row rendering
- **WHEN** the words page lists a phrase
- **THEN** the row wraps the phrase text and the retention indicator works as for words

### Requirement: Phrase translations are edited as a whole string
The edit dialog SHALL edit a phrase's translation as one multi-line string; saving SHALL NOT split it on punctuation. The German text of a phrase SHALL be immutable, as for words.

#### Scenario: Editing keeps sentence punctuation
- **WHEN** the user saves the translation "Извини, что я опоздал."
- **THEN** the stored translation is that exact single entry

### Requirement: Phrase lesson trials require an exact typed answer
A phrase trial SHALL present the translation as prompt and require typing the full German phrase. Grading SHALL compare answer and reference after a grading-only normalization that forgives typography exclusively: apostrophe variants (U+0027, U+2019, U+02BC) are removed, unicode quotes and dashes become spaces, plus the existing case/punctuation/whitespace/umlaut folding. No self-assessment buttons SHALL exist. The grading normalization SHALL be a separate function from the id normalization.

#### Scenario: Typographic variants pass
- **WHEN** the reference is "Wie geht's?" and the user types "wie gehts"
- **THEN** the answer is graded correct

#### Scenario: Wrong word fails
- **WHEN** the user replaces a word of the phrase with a different word
- **THEN** the answer is graded incorrect

### Requirement: A wrong phrase answer uses the existing error footer
On an incorrect phrase answer, the error state SHALL show the typed answer and the reference as plain text and offer the same single action every other trial type offers. The footer SHALL NOT gain a phrase-specific action, and where the answer differs SHALL NOT be marked up: both belong to a redesign that covers word and example trials as well.

#### Scenario: Wrong phrase answer ends the attempt
- **WHEN** the user answers a phrase trial incorrectly
- **THEN** the footer shows the answer, the reference, and the same continue action as a word trial

### Requirement: Every graded phrase attempt writes a review
A phrase trial SHALL write a review document on every graded attempt, as a word trial does. A trial answered wrongly stays in the pool and may be graded again in the same lesson, and each of those attempts SHALL be recorded.

#### Scenario: A correction within the lesson is recorded
- **WHEN** the user fails a phrase trial and later succeeds at it within the same lesson
- **THEN** both attempts are written as reviews

### Requirement: Phrase conflicts and import merge without splitting
The client conflict resolver SHALL resolve phrase documents in the same pass and with the same strategy as any other vocabulary document (translation union) while keeping translation entries whole. Data import SHALL apply the same last-write-wins merging.

#### Scenario: Two devices add the same phrase
- **WHEN** two devices add the same phrase with different translations and sync
- **THEN** one phrase document remains with both translation entries intact
