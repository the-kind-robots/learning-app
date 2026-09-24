## MODIFIED Requirements

### Requirement: An empty vocabulary shows the first-run invitation

The word list SHALL show the first-run state when the vocabulary in the active
scope holds no words at all: the text "Слов пока нет", the hint "Добавьте
первое слово на главной странице" and a call-to-action leading to the home
page. The search bar and the lesson button SHALL stay hidden, since there is
nothing to search or study.

#### Scenario: Vocabulary holds no words

- **WHEN** the word list is rendered with no words in the active scope and no search query
- **THEN** the first-run text, hint and call-to-action are shown
- **AND** no search box or lesson button is rendered

### Requirement: A filter with no matches says so and keeps the search box

The word list SHALL show a message scoped to the filter — "Ничего не найдено"
with the hint "Попробуйте другой запрос" — when the vocabulary in the active
scope holds words but the active search query matches none of them, and SHALL
NOT claim the vocabulary is empty or offer the first-run call-to-action. The
search bar and the lesson button SHALL remain on screen, with the current query
in the search box, so it can be edited or cleared in place.

#### Scenario: Search matches nothing

- **WHEN** the word list is rendered with words in the active scope and a search query that matches none of them
- **THEN** "Ничего не найдено" and "Попробуйте другой запрос" are shown
- **AND** the first-run text and call-to-action are absent
- **AND** the search box is still rendered, holding the current query

#### Scenario: Clearing the filter restores the list

- **WHEN** the user clears a search query that matched nothing
- **THEN** the list of words returns without a page reload
