## ADDED Requirements

### Requirement: An empty vocabulary shows the first-run invitation

The word list SHALL show the first-run state when the vocabulary in the active
scope holds no words at all: the text "Слов пока нет", the hint "Добавьте
первое слово на главной странице" and a call-to-action leading to the home
page. Header, search box and lesson footer SHALL stay hidden, since there is
nothing to search or study.

#### Scenario: Vocabulary holds no words

- **WHEN** the word list is rendered with no words in the active scope and no search query
- **THEN** the first-run text, hint and call-to-action are shown
- **AND** no search box, header or lesson button is rendered

### Requirement: A filter with no matches says so and keeps the search box

The word list SHALL show a message scoped to the filter — "Ничего не найдено"
with the hint "Попробуйте другой запрос" — when the vocabulary in the active
scope holds words but the active search query matches none of them, and SHALL
NOT claim the vocabulary is empty or offer the first-run call-to-action. The header,
search box and lesson footer SHALL remain on screen, with the current query in
the search box, so it can be edited or cleared in place.

#### Scenario: Search matches nothing

- **WHEN** the word list is rendered with words in the active scope and a search query that matches none of them
- **THEN** "Ничего не найдено" and "Попробуйте другой запрос" are shown
- **AND** the first-run text and call-to-action are absent
- **AND** the search box is still rendered, holding the current query

#### Scenario: Clearing the filter restores the list

- **WHEN** the user clears a search query that matched nothing
- **THEN** the list of words returns without a page reload

### Requirement: The presenter decides which empty state applies

The word list view SHALL NOT compute the distinction between an empty
vocabulary and a filtered-out list. `pages.words.presenter` SHALL derive it from
the row count in the active scope and the rows left after filtering, and SHALL
hand the view ready props: the list rows, the empty-state text, hint and
call-to-action, and whether the page chrome applies.

#### Scenario: Matching rows leave no empty state

- **WHEN** the presenter is given rows that survived the filter
- **THEN** it returns those rows and no empty-state props

#### Scenario: Presenter distinguishes the two empty cases

- **WHEN** the presenter is given no rows and a scope total of zero
- **THEN** it returns the first-run empty-state props
- **WHEN** the presenter is given no rows and a non-zero scope total
- **THEN** it returns the no-matches empty-state props
