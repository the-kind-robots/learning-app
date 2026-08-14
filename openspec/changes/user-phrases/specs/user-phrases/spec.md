# user-phrases Specification

## ADDED Requirements

### Requirement: Phrases are added from the home form with automatic mode detection
The home add form SHALL detect whether the input is a word or a phrase without a manual toggle. Input containing a space (after trim) SHALL be treated as a phrase, EXCEPT when it is an article (der/die/das/ein/eine) followed by a single token, `sich` followed by a single token, or it matches a non-phrase dictionary lemma from already-fetched completions. Selecting an autocomplete suggestion SHALL set the mode from the suggestion: multi-word pos=phrase suggestions select phrase mode, all others word mode. A tappable chip SHALL appear when phrase mode is detected and SHALL let the user override the detected mode until the form is cleared.

#### Scenario: Space switches to phrase mode
- **WHEN** the user types "auf jeden Fall" into the German field
- **THEN** the form enters phrase mode and shows the tappable "фраза" chip
- **AND** open suggestions are cleared and stale async completion responses do not prefill the translation

#### Scenario: Article plus noun stays a word
- **WHEN** the user types "der Tisch" or "sich freuen"
- **THEN** the form stays in word mode and autocomplete keeps working

#### Scenario: Chip overrides detection
- **WHEN** the user taps the "фраза" chip
- **THEN** the mode flips to word and the override persists until the form is cleared

#### Scenario: Mode flip preserves input
- **WHEN** the detected mode changes while the user is typing
- **THEN** the entered German text and translation are preserved (inputs are not remounted)

### Requirement: Phrase and translation inputs are multi-line
Both the German input and the translation input SHALL be auto-growing multi-line fields: single-row when compact, growing with content up to a maximum height, then scrolling internally. Auto-growing SHALL work on Safari (JS fallback when `field-sizing: content` is unsupported). Line breaks SHALL be visual only and SHALL be collapsed to single spaces on submit.

#### Scenario: Long phrase grows the field
- **WHEN** the user pastes a phrase longer than one visual line
- **THEN** the field grows to show the content up to the maximum height without horizontal scrolling

### Requirement: Adding a phrase creates a phrase document without example generation
Submitting the form in phrase mode SHALL create a `phrase` document with the translation stored as a single entry (never split on punctuation), seed an initial review, and add the phrase to the active collection. No LLM example fetch SHALL be queued for phrases. Re-adding an existing phrase SHALL merge translations as whole entries and add it to the active collection.

#### Scenario: Phrase with sentence translation survives punctuation
- **WHEN** the user adds "Entschuldigung, dass ich zu spät komme" with translation "Извини, что я опоздал."
- **THEN** one phrase document exists whose translation is the single entered string
- **AND** no example-fetch task is queued

#### Scenario: Duplicate phrase merges
- **WHEN** the user adds a phrase whose normalized value already exists as a phrase document
- **THEN** the translations are merged as whole entries and the document count does not grow

### Requirement: A phrase and a word may share a value
A word and a phrase carrying the same normalized value are distinct learnable entities in distinct id namespaces. Submitting a phrase whose value already exists as a word SHALL create it without a warning, a confirmation, or a second press.

#### Scenario: Phrase matching an existing multi-word vocab word
- **WHEN** the user submits a phrase whose normalized value equals an existing `vocab:` document's value
- **THEN** the phrase document is created on the first submit and both entries keep their own review logs

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

### Requirement: Phrase errors preserve the answer for a retry
On an incorrect phrase answer, the error state SHALL show the typed answer and the reference as plain text. A primary retry action SHALL return to the input with the previous answer preserved; a secondary action SHALL move on (trial stays in the pool). Marking up where the answer differs is out of scope: it has to work for word and example trials too, over one grading normalization.

#### Scenario: Retry keeps the typed text
- **WHEN** the user chooses "ИСПРАВИТЬ"
- **THEN** the answer field contains the previous answer with the cursor at the end

### Requirement: Phrase reviews are written at most once per trial per lesson
Only the first graded attempt of a phrase trial within a lesson SHALL write a review document; later attempts of the same trial in that lesson SHALL NOT write reviews.

#### Scenario: Repeated failures count once
- **WHEN** the user fails the same phrase trial three times and then succeeds within one lesson
- **THEN** exactly one review document is written for that trial (the first, failed attempt)

### Requirement: Phrase conflicts and import merge without splitting
The client conflict resolver SHALL resolve `phrase:` documents with the same strategy as vocab (translation union) while keeping translation entries whole. Data import SHALL apply last-write-wins merging to `phrase` documents as it does for vocab.

#### Scenario: Two devices add the same phrase
- **WHEN** two devices add the same phrase with different translations and sync
- **THEN** one phrase document remains with both translation entries intact
