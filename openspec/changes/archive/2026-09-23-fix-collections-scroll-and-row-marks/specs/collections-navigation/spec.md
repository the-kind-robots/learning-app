## MODIFIED Requirements

### Requirement: A tap the browser cancels without movement still opens the collection
When the browser cancels a touch on a collection card (`pointercancel` with no `click` following), the card SHALL judge it when the finger lifts, not when the browser cancels it: it is a tap if the finger travelled at most 10 px from where it went down and the page scrolled at most 2 px, both measured over the whole touch up to the lift. A touch the browser cancels and then ends without a lift (`touchcancel`) activates nothing. A long press SHALL give up when the finger travels more than 10 px, whether that movement comes before or after the cancel. A gesture SHALL fire at most once: a `click` the browser delivers after such a recovered tap does nothing.

#### Scenario: Cancelled tap without movement
- **WHEN** the user taps a collection card and the browser cancels the touch
- **AND** the finger lifts after at most 10 px of travel and at most 2 px of page scroll
- **THEN** the collection is activated as if the tap had completed

#### Scenario: A swipe that starts on a card
- **WHEN** the user puts a finger on a collection card and swipes to scroll the screen
- **AND** the browser cancels the touch before the page has scrolled and before any pointer movement was reported
- **THEN** the screen scrolls and nothing is activated

#### Scenario: Cancelled touch that moved
- **WHEN** the browser cancels a touch on a card and the finger travels more than 10 px or the page scrolls more than 2 px before it lifts
- **THEN** nothing is activated

## ADDED Requirements

### Requirement: The delete mark takes the place of the count
In editing mode the delete mark (✕) of a tile, a folder header or a folder row SHALL stand where that target's word count stands, and the count SHALL be hidden while the mark is shown. The mark SHALL NOT overlap the name, and the name SHALL keep the same width and the same place in its tile in and out of editing mode.

#### Scenario: A row with a long name in editing mode
- **WHEN** the row `Meetings und Besprechungen mit Kollegen` under the folder `Arbeit` is in editing mode on a 384 × 800 viewport
- **THEN** the ✕ stands where the row's count was, the count is not shown, and the ✕ does not overlap the name

#### Scenario: Entering editing mode moves no text
- **WHEN** a tile, a folder header or a folder row enters editing mode
- **THEN** its name keeps its width and its place in the tile

### Requirement: Long collection names break at syllables
A collection name SHALL be marked as German and, where a word does not fit its line, SHALL break at a syllable boundary with a hyphen. A string with no hyphenation point SHALL still break rather than overflow its tile. «Всё подряд» is not a collection name and is not marked German.

#### Scenario: A long German word
- **WHEN** a folder row shows `Unterkunftsmöglichkeiten` on a 384 × 800 viewport and the word does not fit one line
- **THEN** it breaks at a syllable with a hyphen at the end of the first line

#### Scenario: An unbreakable string
- **WHEN** a collection name is a string longer than its tile with no hyphenation point
- **THEN** the name wraps within its tile and nothing overflows
