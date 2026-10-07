const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// A screen renders in the task of the tap that opened it (#494), so the
// second click of a double click lands on the new screen. It must not act
// there: a double click is one activation of the control it started on.

const homeHeading = (page) => page.getByRole('heading', { name: 'Главная' });

// Read at the engine level, as test/browser/README.md prescribes.
async function reviewCount(page) {
  return page.evaluate(async () => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    const found = await db.find_all(db.use('user-db'), toClj({ selector: { type: 'review' } }));
    return cljs.core.count(cljs.core.get(found, kw('docs')));
  });
}

// Establishing that something never happens takes letting time pass
// (test/browser/README.md, "Conventions").
const nothingHappensFor = (page, ms) => page.waitForTimeout(ms);

test('a double click on «НАЧАТЬ УРОК» starts the lesson and checks no answer', async ({ page }) => {
  await page.goto('/home');
  await addWord(page, 'Haus', 'дом');
  await addWord(page, 'Hund', 'пёс');
  const before = await reviewCount(page);

  await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).dblclick();

  await expect(page).toHaveURL(/\/lesson$/);
  await expect(page.locator('#lesson-answer')).toBeVisible();
  await nothingHappensFor(page, 1000);
  await expect(page.getByRole('heading', { name: 'Ваш ответ:' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Правильно!' })).toHaveCount(0);
  expect(await reviewCount(page)).toBe(before);
});

test('a double click on the corner ✕ closes the screen and opens nothing else', async ({ page }) => {
  await page.goto('/home');
  await addWord(page, 'Haus', 'дом');
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page.locator('.word-item')).toHaveCount(1);

  // Where ✕ was, home shows the themes icon.
  await page.getByRole('button', { name: 'Закрыть' }).dblclick();

  await expect(homeHeading(page)).toBeAttached();
  await nothingHappensFor(page, 1000);
  await expect(page).toHaveURL(/\/home$/);
  await expect(homeHeading(page)).toBeAttached();
});

test('a blank answer is not checked', async ({ page }) => {
  await page.goto('/home');
  await addWord(page, 'Haus', 'дом');
  await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
  await expect(page.locator('#lesson-answer')).toBeVisible();
  const before = await reviewCount(page);

  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();

  await nothingHappensFor(page, 1000);
  await expect(page.locator('#lesson-answer')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Ваш ответ:' })).toHaveCount(0);
  expect(await reviewCount(page)).toBe(before);
});
