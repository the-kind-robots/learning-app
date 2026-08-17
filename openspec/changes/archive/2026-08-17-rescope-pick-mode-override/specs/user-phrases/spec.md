## MODIFIED Requirements

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
