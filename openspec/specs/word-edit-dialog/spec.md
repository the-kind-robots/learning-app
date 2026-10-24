# word-edit-dialog Specification

## Purpose
Define what the word edit dialog shows of the word's example and how the learner changes it.

## Requirements

### Requirement: The edit dialog writes the word, its translations and an example for every collection of the word

The word edit dialog SHALL write the word as a borderless field and its translations as one text, one translation to a line, under it. «Такое слово уже есть» SHALL stand under the word when its spelling is another word's; the notice that a changed spelling is saved as a new word SHALL stand under the translations. Under them a strip of cards SHALL show the word's examples (`specs/examples-schema/spec.md`), one card for every collection of the word in every scope, each 290 px wide, side by side, snapping while it scrolls, all as tall as the tallest. A card's border SHALL be in the colour the themes screen gives the tile of the collection's folder, grey for «Без темы». A card SHALL say the path of its collection on one line, cut to its first and last segments when it has three or more and is longer than 30 characters. Under the path a card SHALL show the sentence with the word set in bold and, below it, the sentence's translation; the learner SHALL write both in place by tapping them, without a mode, and entering the text SHALL NOT move anything. A card's button «Другой пример» SHALL ask `POST /api/examples` for the word with its part of speech, the card's collection name as the context, and the sentence on the card in `avoid`; while it waits the icon SHALL spin, every such button SHALL be off, the sentence SHALL fade, and «Подбираю пример…» SHALL stand where the translation was; a failed request SHALL say «Не получилось. Нажмите ↻ ещё раз.» there and leave the example. The footer SHALL hold a delete button (labelled «Удалить слово»), «Сбросить» and «Сохранить». «Сбросить» SHALL bring back the word, the translations and the examples the dialog opened with and keep the dialog open. «Сбросить» and «Сохранить» SHALL be off while nothing is changed; «Сохранить» SHALL also be off while the spelling is blank or another word's, or a changed example lacks its sentence or its translation. «Сохранить» SHALL save the word and replace the example of every card that was changed, and close the dialog once the replacements are written; a word whose spelling is changed SHALL NOT have its examples replaced. A tap on the backdrop and Esc SHALL close the dialog and drop what was not saved, and abort a request that is out.

#### Scenario: A word in several collections
- **WHEN** the user opens the word «Bahnhof», which lies in «Reisen/Bahn» and «Alltag» and has an example of no collection
- **THEN** the dialog shows three cards, «Reisen / Bahn», «Alltag» and «Без темы», each with its sentence, «Bahnhof» in bold

#### Scenario: The scope's collection first
- **WHEN** «Alltag» is the active collection
- **THEN** its card comes first, the others follow

#### Scenario: A different example is asked for
- **WHEN** the user presses «Другой пример» on a card
- **THEN** the request carries the sentence of that card in `avoid` and its collection's name
- **AND** the card shows the sentence that comes back

#### Scenario: The request fails
- **WHEN** the request cannot reach the server
- **THEN** the card says «Не получилось. Нажмите ↻ ещё раз.» and keeps its example

#### Scenario: An example written in place
- **WHEN** the user changes the sentence and translation of a card and presses «Сохранить»
- **THEN** the dialog closes, and reopening the word shows that sentence, and the collection has no other example

#### Scenario: Nothing changed
- **WHEN** the user has changed nothing
- **THEN** «Сбросить» and «Сохранить» are off

#### Scenario: Reset
- **WHEN** the user changes the word, translations and an example and presses «Сбросить»
- **THEN** all three are as they were, and the dialog stays open

#### Scenario: Closing drops the edits
- **WHEN** the user changes an example and presses Esc, or taps the backdrop
- **THEN** reopening the word shows the example it had

#### Scenario: A long path
- **WHEN** a collection is named «Netflix/Babylon Berlin/Staffel 2/Folge 7»
- **THEN** its card says «Netflix / … / Folge 7»
