## ADDED Requirements

### Requirement: A button names its hotkey

A button whose action a hotkey also does SHALL name the key in its tooltip, after its own name («Статистика · Alt+S»), and in `aria-keyshortcuts`: today's stars `Alt`+`S`, the themes icon `Alt`+`T`, «Список слов» `Alt`+`W`, «НАЧАТЬ УРОК» `Alt`+`Enter`, the corner ✕ of the words, themes and statistics screens `Escape`. The ✕ of a lesson SHALL name no key. The button's accessible name SHALL stay its own.

#### Scenario: The buttons of home

- **WHEN** a learner with a word and a lesson today points at the buttons of home
- **THEN** the stars read «Статистика · Alt+S», the themes icon «Наборы · Alt+T», «Список слов» «Список слов · Alt+W» and «НАЧАТЬ УРОК» «НАЧАТЬ УРОК · Alt+Enter»

#### Scenario: The ✕ of a screen

- **WHEN** the learner points at the corner ✕ of the words screen
- **THEN** it reads «Закрыть · Escape»

#### Scenario: The ✕ of a lesson

- **WHEN** the learner points at the corner ✕ in a lesson
- **THEN** it reads «Закрыть» and names no key
