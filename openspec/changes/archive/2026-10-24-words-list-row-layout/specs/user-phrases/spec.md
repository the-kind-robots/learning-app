## MODIFIED Requirements

### Requirement: Phrases appear in the words list
The words page SHALL show phrases in the same list as words, sorted by the same retention order. Search and word counts SHALL include phrases. No type label is shown: the list reads as one vocabulary. A row SHALL show the German text on its first line and the translations on the line below, joined by « · », both wrapped over as many lines as they need and never truncated; the retention indicator SHALL stand at the row's end, centred on its height, and the row SHALL carry no arrow. The type decision SHALL be computed by the presenter, not the view.

#### Scenario: Phrase row rendering
- **WHEN** the words page lists a phrase
- **THEN** the row wraps the phrase text and the retention indicator works as for words

#### Scenario: A word with several translations
- **WHEN** the words page lists «Abendessen» with the translations «ужин» and «вечерняя еда»
- **THEN** its row reads «Abendessen» and, under it, «ужин · вечерняя еда»
