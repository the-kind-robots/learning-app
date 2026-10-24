## ADDED Requirements

### Requirement: The edit dialog shows the word's example and lets the learner change it

The word edit dialog SHALL show, under the translation, under the heading «Пример в теме «<theme>»», the example a lesson in the current scope would pick for the word (`specs/examples-schema/spec.md`): the German sentence with the word set in bold, and its translation below. The dialog SHALL NOT label the example as generated or as written by the learner. «Изменить» SHALL open a form of the German sentence and its translation, both required, prefilled with the current example, with «Готово» and «Отмена». «Сгенерировать» SHALL ask `POST /api/examples` for the word with its part of speech and the current sentence in `avoid`, show «Подбираю…» while it waits, and then show the example that comes back. A failed request SHALL show «Новый пример не пришёл: нет связи с сервером. Текущий остаётся.» with «Повторить», and SHALL leave the current example. Changes to the example SHALL apply on «Сохранить» and be dropped by «Отмена». While the word's text differs from its own, the dialog SHALL NOT offer to change the example.

#### Scenario: A word with an example
- **WHEN** the user opens the word «Bahnhof» in the theme «Bahn», which has one example for it
- **THEN** the dialog shows that sentence with «Bahnhof» in bold, its translation, and the heading «Пример в теме «Bahn»»

#### Scenario: A different example is asked for
- **WHEN** the user presses «Сгенерировать»
- **THEN** the request carries the current sentence in `avoid`
- **AND** the dialog shows the sentence that comes back

#### Scenario: The request fails
- **WHEN** the request cannot reach the server
- **THEN** the dialog shows «Новый пример не пришёл: нет связи с сервером. Текущий остаётся.» and «Повторить», and the current example stays

#### Scenario: Cancelling drops the change
- **WHEN** the user generates or writes an example and presses «Отмена» of the dialog
- **THEN** reopening the word shows the example it had before

#### Scenario: The learner's own example
- **WHEN** the user writes a sentence and its translation, presses «Готово» and then «Сохранить»
- **THEN** reopening the word shows that sentence

### Requirement: In the main scope the dialog offers a theme for every source of the word's examples

In the main scope («Всё подряд»), when the word shows examples of more than one collection (`specs/examples-schema/spec.md`), the dialog SHALL show under the heading «Пример» a chip for every theme that lists the word, ordered by name, and a chip «Без темы» last when the word has an example with no theme. A chip SHALL say the last segment of its theme's name after «/»; a name longer than 18 characters SHALL be shortened in the middle at word boundaries: its first word (cut to 10 characters when longer than 12), «… », and the last words while their joined length is at most 10, the last word always; a name of one word stays whole. Every chip SHALL have the same border and weight, the border in the colour the themes screen gives the tile of the theme's folder, and neutral grey for «Без темы»; the chosen chip SHALL differ only by a fill and `aria-pressed`. Chips SHALL wrap onto further lines and SHALL NOT scroll. Under the chips one line SHALL say the full name of the chosen theme, or «Без темы». The dialog SHALL open on the pair of the example a lesson in the main scope asks. Choosing a chip SHALL choose the pair «Изменить» and «Сгенерировать» act on, and the generation request SHALL carry that theme's name as its context, none for «Без темы». The chips SHALL be disabled while the form of an own example is open or a request is out, and a request that is out SHALL be aborted when the dialog closes. The cards of all pairs SHALL stand in one place, only the chosen one visible, so that choosing a chip moves neither the chips nor the lines under them and changes no height. Inside a named theme there SHALL be no chips and the heading SHALL be «Пример в теме «<theme>»».

#### Scenario: Choosing another theme
- **WHEN** in the main scope the user chooses the chip of another theme
- **THEN** the dialog shows that theme's example, and the chips and the card keep their places and heights

#### Scenario: A word with an example of no theme
- **WHEN** in the main scope the word has an example with no collection
- **THEN** the chips end with «Без темы»

#### Scenario: A long name
- **WHEN** a theme is named «Prüfung/Goethe-Zertifikat B1 Wortschatz Teil 2»
- **THEN** its chip says «Goethe-Zer… Teil 2»
