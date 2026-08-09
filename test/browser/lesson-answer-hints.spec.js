const { test, expect } = require('@playwright/test');

// Answer-hint popover in the lesson (GH-273). Scenarios:
//
// 1. Example answered correctly → the revealed answer carries clickable
//    annotated words; answered incorrectly → same, under «Правильно:».
// 2. Word not in the vocabulary → hint card offers «+ В СЛОВАРЬ»;
//    word already in the vocabulary → card shows «✓ В словаре» instead.
// 3. Adding from the card updates the same popover in place to the
//    "added" state and the word lands in the vocabulary list.
// 4. The popover appears on token click, hides instantly on a click
//    outside, and hides after a timeout once the pointer leaves it.
//
// The examples backend needs an external API, so the example document is
// seeded straight into the app's device-db through the dev-build globals —
// the same layer the app itself uses (see test/browser/README.md).

async function addWord(page, value, translation) {
  await page.getByLabel('Слово (немецкий)').fill(value);
  await page.getByLabel('Перевод (русский)').fill(translation);
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
  await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('');
}

async function seedExample(page) {
  await page.evaluate(async () => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    const dbs = await db.pouch.init_BANG_(null);
    const found = await db.pouch.find_all(dbs, toClj({ selector: { type: 'vocab' } }));
    const wordId = cljs.core.get(cljs.core.first(cljs.core.get(found, kw('docs'))), kw('_id'));
    await db.pouch.insert(dbs, toClj({
      'type': 'example',
      'word-id': wordId,
      'word': 'der Hund',
      'value': 'Der Hund schläft im Garten.',
      'translation': 'Пёс спит в саду.',
      'created-at': new Date().toISOString(),
      'structure': [
        { usedForm: 'Hund', dictionaryForm: 'der Hund', translation: 'пёс', wordIndex: 1 },
        { usedForm: 'Garten', dictionaryForm: 'der Garten', translation: 'сад', wordIndex: 4 },
      ],
    }));
  });
}

// Answers the word trial, advances, and answers the example trial with
// `exampleAnswer`. Returns with the revealed answer on screen.
async function playToExampleReveal(page, exampleAnswer) {
  await page.goto('/lesson');
  await expect(page.locator('.lesson__prompt')).toBeVisible();
  await page.locator('#lesson-answer').fill('der Hund');
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(page.locator('.lesson__instruction')).toContainText('предложение');
  await page.locator('#lesson-answer').fill(exampleAnswer);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
}

async function setUpLesson(page, exampleAnswer) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await seedExample(page);
  await playToExampleReveal(page, exampleAnswer);
}

const popover = (page) => page.locator('#popover');
const token = (page, index) => page.locator(`.lesson__answer-token[data-word-index="${index}"]`);

test('correct example answer reveals clickable annotated words', async ({ page }) => {
  await setUpLesson(page, 'Der Hund schläft im Garten.');
  await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
  await expect(token(page, 1)).toHaveText('Hund');
  await expect(token(page, 4)).toHaveText('Garten.');
});

test('wrong example answer reveals the correct answer with annotated words', async ({ page }) => {
  await setUpLesson(page, 'Die Katze schläft.');
  await expect(page.getByRole('heading', { name: 'Правильно:' })).toBeVisible();
  await expect(token(page, 4)).toHaveText('Garten.');
});

test('unknown word offers add, known word shows already-in-dictionary', async ({ page }) => {
  await setUpLesson(page, 'Der Hund schläft im Garten.');

  await token(page, 4).click();
  await expect(popover(page).locator('.token-card__word')).toHaveText('der Garten');
  await expect(popover(page).getByRole('button', { name: '+ В СЛОВАРЬ' })).toBeVisible();

  await token(page, 1).click();
  await expect(popover(page).locator('.token-card__word')).toHaveText('der Hund');
  await expect(popover(page).locator('.token-card__state')).toHaveText('✓ В словаре');
  await expect(popover(page).getByRole('button')).toHaveCount(0);
});

test('adding a word updates the card in place and persists the word', async ({ page }) => {
  await setUpLesson(page, 'Der Hund schläft im Garten.');

  await token(page, 4).click();
  await popover(page).getByRole('button', { name: '+ В СЛОВАРЬ' }).click();

  // The same popover flips to the added state without closing.
  await expect(popover(page).locator('.token-card__state')).toHaveText('✓ В словаре');
  await expect(popover(page).locator('.token-card__word')).toHaveText('der Garten');

  await page.goto('/words');
  await expect(page.locator('.word-item__value').filter({ hasText: 'der Garten' })).toBeVisible();
});

test('revealed answer selects as continuous text across hinted words', async ({ page }) => {
  await setUpLesson(page, 'Der Hund schläft im Garten.');
  const body = page.locator('.lesson__answer-body');
  const box = await body.boundingBox();
  await page.mouse.move(box.x + 1, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width - 1, box.y + box.height / 2, { steps: 8 });
  await page.mouse.up();
  const selected = await page.evaluate(() => window.getSelection().toString());
  expect(selected.replace(/\s+/g, ' ').trim()).toContain('Der Hund schläft im Garten.');
});

test('popover light-dismisses instantly and auto-closes after pointer leaves', async ({ page }) => {
  await setUpLesson(page, 'Der Hund schläft im Garten.');

  // Click outside → gone immediately.
  await token(page, 4).click();
  await expect(popover(page)).toBeVisible();
  await page.locator('.lesson__prompt').click();
  await expect(popover(page)).toBeHidden({ timeout: 500 });

  // Pointer parked on the card holds it open; leaving arms the close timer.
  await token(page, 4).click();
  await expect(popover(page)).toBeVisible();
  const card = await popover(page).locator('.token-card').boundingBox();
  await page.mouse.move(card.x + card.width / 2, card.y + card.height / 2);
  await page.mouse.move(5, 5);
  await expect(popover(page)).toBeHidden({ timeout: 3000 });
});
