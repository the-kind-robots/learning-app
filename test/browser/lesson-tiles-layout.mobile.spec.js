const { test, expect, docsOfType } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// A long example in tiles on a phone (GH-573): the bank sits above the tray in
// the lesson body, and when the sentence does not fit, the body scrolls instead
// of clipping the prompt.

const SENTENCE =
  'Gestern Abend habe ich meinen Schlüssel im Büro vergessen und musste ' +
  'meine Nachbarn bitten, uns in die Wohnung zu lassen.';
const PROMPT =
  'Вчера вечером я забыл ключ в офисе, и мне пришлось попросить соседей ' +
  'впустить нас в квартиру.';

async function seedLongExample(page) {
  const [{ _id: wordId }] = await docsOfType(page, 'user-db', 'vocab');
  await page.evaluate(async ([wordId, sentence, prompt]) => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    await db.insert(db.use('user-db'), adapters.learner.documents.example_doc(wordId, 'der Hund', null, toClj({
      'value': sentence,
      'translation': prompt,
      'structure': [],
    })));
  }, [wordId, SENTENCE, PROMPT]);
}

async function showLongExample(page) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await seedLongExample(page);
  await page.goto('/lesson');
  await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('der Hund');
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
}

for (const height of [844, 667]) {
  test(`ученик открыл плитки длинного примера на экране ${height} → задание целиком в окне урока, слова над лотком и не налезают на него`, async ({ page }) => {
    await test.step('Дано длинный пример на экране телефона', async () => {
      await page.setViewportSize({ width: 390, height });
      await showLongExample(page);
    });

    await test.step('Когда плитки на экране', async () => {
      await expect(page.getByRole('group', { name: 'Плитки' })).toBeVisible();
    });

    await test.step('Тогда задание внутри окна урока, слова ниже задания и выше лотка', async () => {
      const box = (locator) => locator.boundingBox();
      const body = await box(page.locator('.lesson__body'));
      const prompt = await box(page.locator('.lesson__prompt'));
      const bank = await box(page.getByRole('group', { name: 'Плитки' }));
      const tray = await box(page.getByRole('group', { name: 'Ваш ответ' }));
      expect(prompt.y).toBeGreaterThanOrEqual(body.y);
      expect(prompt.y + prompt.height).toBeLessThanOrEqual(body.y + body.height);
      expect(bank.y).toBeGreaterThanOrEqual(prompt.y + prompt.height);
      expect(body.y + body.height).toBeLessThanOrEqual(tray.y);    });
  });
}
