const { test, expect } = require('@playwright/test');

// A phrase gets an example, like a word does (GH-371). The path CI does not
// otherwise exercise: add form -> example-fetch task -> /api/examples -> stored
// example -> a lesson trial locked behind the phrase trial.
//
// The endpoint is stubbed with `page.route`. The suite's backend has no
// provider key, so a real call is neither possible nor wanted, and the point
// here is our side of the exchange: that the request is made for the phrase,
// that the answer is stored, and that the lesson then behaves.

const PHRASE = 'auf jeden Fall';
const GLOSS = 'в любом случае';
const SENTENCE = 'Ich komme auf jeden Fall mit.';
const SENTENCE_RU = 'Я обязательно пойду вместе.';

// Plain word-by-word annotation: `structure` says nothing about which words
// belonged to the construction. `auf` and `jeden` are a preposition and a
// determiner, so they are left out like any others, and `Fall` is annotated as
// the noun it is.
const STRUCTURE = [
  { usedForm: 'komme', dictionaryForm: 'mitkommen', translation: 'идти вместе', wordIndex: 1 },
  { usedForm: 'Fall', dictionaryForm: 'der Fall', translation: 'случай', wordIndex: 4 },
  { usedForm: 'mit', dictionaryForm: 'mitkommen', translation: 'идти вместе', wordIndex: 5 },
];

// Returns the URLs the app asked the endpoint for, growing as it asks.
async function stubExampleEndpoint(page) {
  const requested = [];
  await page.route('**/api/examples*', async (route) => {
    requested.push(route.request().url());
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        value: SENTENCE,
        translation: SENTENCE_RU,
        structure: STRUCTURE,
      }),
    });
  });
  return requested;
}

async function addPhrase(page) {
  await page.getByLabel('Слово (немецкий)').fill(PHRASE);
  // The form has no mode control: the label is how it says which kind it is
  // about to save, so this is also the assertion that it detected a phrase.
  await expect(page.getByLabel('Фраза (немецкий)')).toHaveValue(PHRASE);
  await page.getByLabel('Перевод (русский)').fill(GLOSS);
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
  await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('');
}

// The stored example is the only honest evidence that the answer landed: it is
// written by the task runner, and nothing renders it until a lesson starts.
// Read at the engine level, as test/browser/README.md prescribes.
async function storedExampleValues(page) {
  return page.evaluate(async () => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    const found = await db.find(db.use('device-db'), toClj({ selector: { type: 'example' } }));
    const docs = cljs.core.get(found, kw('docs'));
    return cljs.core.clj__GT_js(cljs.core.mapv((d) => cljs.core.get(d, kw('value')), docs));
  });
}

async function addPhraseAndAwaitItsExample(page) {
  const requested = await stubExampleEndpoint(page);
  await page.goto('/home');
  await addPhrase(page);

  // The task runner decides when the fetch goes out, so wait for the
  // condition rather than for a duration.
  await expect.poll(() => requested.length, { timeout: 15000 }).toBeGreaterThan(0);
  expect(decodeURIComponent(requested[0])).toContain(`word=${PHRASE}`);
  await expect.poll(() => storedExampleValues(page), { timeout: 15000 }).toEqual([SENTENCE]);
}

const progressNow = (page) => page.getByRole('progressbar');

test('a phrase asks for an example and the lesson locks it behind the phrase trial', async ({ page }) => {
  await addPhraseAndAwaitItsExample(page);

  await page.goto('/lesson');

  // Two trials exist, and the example is not one the lesson will offer yet:
  // the only selectable trial is the phrase's own.
  await expect(page.locator('.lesson__instruction')).toHaveText('Переведите фразу на немецкий');
  await expect(page.locator('.lesson__prompt')).toHaveText(GLOSS);
  await expect(progressNow(page)).toHaveAttribute('aria-valuenow', '0');

  await page.locator('#lesson-answer').fill(PHRASE);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();

  // Half, not all: the locked example counted as a lesson trial all along. Had
  // the phrase produced no example trial, this answer would have finished the
  // lesson.
  await expect(progressNow(page)).toHaveAttribute('aria-valuenow', '50');

  // And now it is selectable — the unlock the correct phrase answer performed.
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(page.locator('.lesson__instruction')).toHaveText('Переведите предложение на немецкий');
  await expect(page.locator('.lesson__prompt')).toHaveText(SENTENCE_RU);

  await page.locator('#lesson-answer').fill(SENTENCE);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
  await expect(progressNow(page)).toHaveAttribute('aria-valuenow', '100');
});

test('the example is annotated word by word, with no trace of the construction', async ({ page }) => {
  await addPhraseAndAwaitItsExample(page);

  await page.goto('/lesson');
  await page.locator('#lesson-answer').fill(PHRASE);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(page.locator('.lesson__instruction')).toHaveText('Переведите предложение на немецкий');
  await page.locator('#lesson-answer').fill(SENTENCE);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();

  // `structure` carries no membership, so the hover on a word of the
  // construction shows that word's own lemma. `Fall` reads as «случай», and
  // `auf` and `jeden` are plain words with no card at all.
  const token = (index) => page.locator(`.lesson__answer-token[data-word-index="${index}"]`);
  await expect(token(4)).toHaveText('Fall');
  await expect(token(2)).toHaveCount(0);
  await expect(token(3)).toHaveCount(0);

  await token(4).click();
  const card = page.locator('#popover').locator('.token-card__word');
  await expect(card).toHaveText('der Fall');
  await expect(card).not.toHaveText(PHRASE);
});
