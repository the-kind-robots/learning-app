const { test, expect } = require('@playwright/test');

// The list is ordered and paged off the view's key, so what a page holds and
// in what order is one question. Rows are located by their class for the same
// reason `words-paging.spec.js` does it: `listitem` also matches the sentinel.
const rows = (page) => page.locator('li.word-item');

// The issue's own six words. `aufstehen` before `das Auto` — `auf` sorts
// before `aut`, whatever the issue's illustration says; the article is what is
// under test, and it puts Auto under A, Bank under B and Zug under Z.
const SIX = ['der Hund', 'die Katze', 'das Auto', 'der Zug', 'die Bank', 'aufstehen'];
const FILED = ['aufstehen', 'das Auto', 'die Bank', 'der Hund', 'die Katze', 'der Zug'];

const PAGE_SIZE = 50;

// Seeded at the engine level (see README, "Seeding from a spec"). The id is
// what the app itself would store the value under — "vocab:" plus the
// normalised value, article included — because that is the defect: the id
// keeps the article and the list used to be ordered by it.
async function seed(page, values) {
  await page.evaluate(async (vals) => {
    const now = new Date().toISOString();
    const docs = vals.map((value, i) => ({
      _id: 'vocab:' + value.toLowerCase(),
      type: 'vocab',
      value,
      translation: [{ lang: 'ru', value: 'перевод' + i }],
      created_at: now,
      modified_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, values);
}

async function openWords(page) {
  await page.goto('/words');
  await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible({ timeout: 20000 });
}

test('a noun is filed under its word, not under its article', async ({ page }) => {
  await page.goto('/');
  await seed(page, SIX);
  await openWords(page);

  await expect(rows(page)).toHaveCount(SIX.length);
  await expect(rows(page)).toHaveText(FILED.map((value) => new RegExp(value)));
  await page.screenshot({ path: 'test-results/words-order/six-words.png', fullPage: false });
});

// 60 nouns whose articles cycle, so ordering by the stored id would put all
// twenty `das` words first and all twenty `die` words last. Ordering by the
// word puts them in numeric order, and the page boundary at 50 is where a sort
// done after the page was cut would show: it can only order what it was given.
const CYCLED = Array.from({ length: 60 }, (_, i) => {
  const article = ['der', 'die', 'das'][i % 3];
  return article + ' Wort' + String(i).padStart(3, '0');
});

test('a page past the first continues the same order', async ({ page }) => {
  await page.goto('/');
  await seed(page, CYCLED);
  await openWords(page);

  await expect(rows(page)).toHaveCount(PAGE_SIZE);
  await expect(rows(page).first()).toContainText('der Wort000');
  await expect(rows(page).nth(PAGE_SIZE - 1)).toContainText('die Wort049');

  await rows(page).last().scrollIntoViewIfNeeded();
  await expect(rows(page)).toHaveCount(CYCLED.length);
  await expect(rows(page).nth(PAGE_SIZE)).toContainText('das Wort050');
  await expect(rows(page).last()).toContainText('das Wort059');

  const rendered = await rows(page).allTextContents();
  const numbers = rendered.map((text) => Number(text.match(/Wort(\d{3})/)[1]));
  expect(numbers).toEqual(numbers.map((_, i) => i));
  await page.screenshot({ path: 'test-results/words-order/paged.png', fullPage: false });
});

test('a word entered with an article is still the same entry', async ({ page }) => {
  await page.goto('/home');

  // Through the add form, so the whole find-by-value path runs: the id did not
  // change, so entering the same value again must find the stored word and
  // merge into it rather than make a second row. The form clearing itself is
  // the app saying the write landed — without waiting for it the second add
  // races the first.
  const add = async (value, translation) => {
    await page.getByLabel('Слово (немецкий)').fill(value);
    await page.getByLabel('Перевод (русский)').fill(translation);
    await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
    await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('');
  };

  await add('der Zug', 'поезд');
  await add('der Zug', 'состав');

  await openWords(page);
  await expect(rows(page)).toHaveCount(1);
  await expect(rows(page).first()).toContainText('der Zug');
  await expect(rows(page).first()).toContainText('поезд');
  await expect(rows(page).first()).toContainText('состав');

  // And editing it still reaches the same document.
  await rows(page).first().getByRole('button').click();
  await page.getByRole('textbox', { name: 'Перевод' }).fill('поезд, состав');
  await page.getByRole('button', { name: 'Сохранить' }).click();
  await expect(rows(page).first()).toContainText('поезд, состав');
  await expect(rows(page)).toHaveCount(1);
});
