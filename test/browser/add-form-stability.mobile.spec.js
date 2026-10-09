const { test, expect } = require('./fixtures');
const { openHome } = require('./service-worker.shared');

// The phone half of the add-form stability specs (GH-289, GH-373). At 390x844
// the suggestion list opens in the page flow under the value field instead of
// overlaying it. Guards: the box is exactly as tall as its rows (the defect in
// #373 was a fixed 176 px height), what it pushes down it pushes by its own
// height and no further than the ceiling, and the field being typed into holds
// still. Geometry only: layout-shift and INP numbers were instrumentation
// that said the same thing less reliably.
//
// The dictionary is the fixture the backend serves from
// LEARNING_APP__DICTIONARY_DIR (see README.md).

// Every prefix matches the fixture, so the list opens on the first keystroke
// and never collapses mid-word.
const WORD = 'Fenster';
const WORD_MATCHES = 2; // das Fenster, die Fensterbank

// `max-height: min(176px, 34svh)`; at 844 px tall 176 is the binding one.
const CEILING_PX = 176;
// `.suggestions { margin: 4px 0 0 }` travels with the list.
const LIST_MARGIN_PX = 4;
// A different lemma for the warm-up: typing the target here would spend the
// very shift the spec measures.
const PROBE = 'Haus';

const topOf = async (locator) => (await locator.boundingBox()).y;

// The list animates its opening over 180 ms; wait for the transitions
// themselves rather than for a guessed time.
const settleSuggestions = (page) =>
  page.locator('.suggestions').evaluate(async (list) => {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    await Promise.all(list.getAnimations().map((a) => a.finished.catch(() => {})));
  });

test.describe('Подсказки при вводе слова на телефоне', () => {
  test('пользователь печатает слово → список подсказок точно по высоте строк, форма сдвигается ровно на него', async ({ page }) => {
    const field = page.getByLabel('Слово (немецкий)');
    const translation = page.getByLabel('Перевод (русский)');
    const submit = page.getByRole('button', { name: 'ДОБАВИТЬ' });
    const options = page.getByRole('option');
    const title = page.getByRole('heading', { name: 'Добавить слово' });
    let restingSubmitY, fieldBefore, titleBefore, translationBefore;

    await test.step('Дано главная в покое: подсказок нет', async () => {
      await openHome(page);
      await expect(field).toBeVisible();
      restingSubmitY = await topOf(submit);
      // Warm up on another word and let the list fold back, so the measured
      // window starts with the page at rest.
      await field.pressSequentially(PROBE);
      await expect(options.first()).toBeVisible();
      await field.fill('');
      await expect(options).toHaveCount(0);
      await settleSuggestions(page);
      await expect.poll(() => topOf(submit)).toBeCloseTo(restingSubmitY, 1);
      fieldBefore = await field.boundingBox();
      titleBefore = await title.boundingBox();
      translationBefore = await translation.boundingBox();
    });

    await test.step('Когда он печатает слово с человеческой скоростью', async () => {
      // 120 ms per key is the measured human cadence (#195), longer than the
      // 100 ms suggest debounce: every keystroke gets its own answer.
      await field.pressSequentially(WORD, { delay: 120 });
      await expect(options).toHaveCount(WORD_MATCHES);
      await settleSuggestions(page);
    });

    await test.step('Тогда поле и заголовок не сдвинулись', async () => {
      expect((await field.boundingBox()).y).toBe(fieldBefore.y);
      expect((await title.boundingBox()).y).toBe(titleBefore.y);
    });

    await test.step('Тогда список ровно по высоте своих строк и ниже потолка', async () => {
      const list = page.locator('.suggestions'); // a box has no accessible handle
      const listBox = await list.boundingBox();
      const inner = await list.evaluate((el) => ({ client: el.clientHeight, content: el.scrollHeight }));
      expect(inner.client).toBe(inner.content);
      expect(listBox.height).toBeLessThan(CEILING_PX);

      const push = (await topOf(submit)) - restingSubmitY;
      // What the list displaces, it displaces by exactly itself.
      expect(Math.round(push)).toBe(Math.round(listBox.height) + LIST_MARGIN_PX);
      expect(Math.round((await translation.boundingBox()).y - translationBefore.y)).toBe(Math.round(push));
      expect(push).toBeLessThanOrEqual(CEILING_PX + LIST_MARGIN_PX);
    });
  });
});
