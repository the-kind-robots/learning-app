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

const LONG_WORD = 'Donaudampfschifffahrtsgesellschaft';

// Fifteen words, the example the layout of #751 was checked on.
const FIFTEEN_WORDS = 'Obwohl es draußen stark regnete, ging der alte Mann mit seinem Hund im Park spazieren.';
const FIFTEEN_WORDS_PROMPT = 'Хотя на улице шёл сильный дождь, старик всё равно гулял с собакой в парке.';

async function seedLongExample(page, sentence = SENTENCE, prompt = PROMPT) {
  const [{ _id: wordId }] = await docsOfType(page, 'user-db', 'vocab');
  await page.evaluate(async ([wordId, sentence, prompt]) => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    await db.insert(db.use('user-db'), adapters.learner.documents.example_doc(wordId, 'der Hund', null, toClj({
      'value': sentence,
      'translation': prompt,
      'structure': [],
    })));
  }, [wordId, sentence, prompt]);
}

async function showLongExample(page, sentence, prompt) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await seedLongExample(page, sentence, prompt);
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
      const prompt = await box(page.locator('#lesson-challenge .lesson__prompt'));
      const bank = await box(page.getByRole('group', { name: 'Плитки' }));
      const tray = await box(page.getByRole('group', { name: 'Ваш ответ' }));
      expect(prompt.y).toBeGreaterThanOrEqual(body.y);
      expect(prompt.y + prompt.height).toBeLessThanOrEqual(body.y + body.height);
      expect(bank.y).toBeGreaterThanOrEqual(prompt.y + prompt.height);
      expect(body.y + body.height).toBeLessThanOrEqual(tray.y);    });
  });
}

// The lesson's look on a phone (#751).

test('ученик на телефоне 360×690 открывает, собирает и проверяет длинный пример → задание целиком над панелью на каждом шаге', async ({ page }) => {
  const bank = page.getByRole('group', { name: 'Плитки' });
  // The whole prompt lies inside the lesson body, which ends where the panel begins.
  const expectPromptInBody = async () => {
    const body = await page.locator('.lesson__body').boundingBox();
    const prompt = await page.locator('#lesson-challenge .lesson__prompt').boundingBox();
    const panel = await page.locator('.lesson__panel').boundingBox();
    expect(prompt.y).toBeGreaterThanOrEqual(body.y);
    expect(prompt.y + prompt.height).toBeLessThanOrEqual(body.y + body.height);
    expect(prompt.y + prompt.height).toBeLessThanOrEqual(panel.y);
  };

  await test.step('Дано длинный пример на экране 360×690', async () => {
    await page.setViewportSize({ width: 360, height: 690 });
    await showLongExample(page, FIFTEEN_WORDS, FIFTEEN_WORDS_PROMPT);
  });

  await test.step('Когда плитки на экране', async () => {
    await expect(bank).toBeVisible();
  });

  await test.step('Тогда задание целиком в окне урока', expectPromptInBody);

  await test.step('Когда он ставит все плитки', async () => {
    while ((await bank.getByRole('button').count()) > 0) {
      await bank.getByRole('button').first().click();
    }
  });

  await test.step('Тогда задание по-прежнему целиком в окне урока', expectPromptInBody);

  await test.step('Когда он проверяет неверный порядок', async () => {
    await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
    await expect(page.getByRole('heading', { name: 'Правильно:' })).toBeVisible();
  });

  await test.step('Тогда задание целиком над панелью ошибки, а панель во всю ширину экрана', async () => {
    await expectPromptInBody();
    const panel = await page.locator('#lesson-footer .page-footer').boundingBox();
    expect(panel.x).toBe(0);
    expect(panel.width).toBe(360);
  });
});

test('ученик на телефоне 360 ставит плитку слова из 34 букв → плитка в поле, не шире его', async ({ page }) => {
  await test.step('Дано пример с длинным словом на экране 360', async () => {
    await page.setViewportSize({ width: 360, height: 690 });
    await showLongExample(page, `Die ${LONG_WORD} fährt heute nicht.`, 'Пароходство сегодня не работает.');
  });

  await test.step('Когда он ставит плитку длинного слова', async () => {
    await page.getByRole('group', { name: 'Плитки' }).getByRole('button', { name: LONG_WORD }).click();
  });

  await test.step('Тогда плитка внутри поля', async () => {
    const field = await page.locator('#lesson-answer').boundingBox();
    const tile = await page.getByRole('group', { name: 'Ваш ответ' }).getByRole('button', { name: LONG_WORD }).boundingBox();
    expect(tile.x).toBeGreaterThanOrEqual(field.x);
    expect(tile.x + tile.width).toBeLessThanOrEqual(field.x + field.width);
  });
});
