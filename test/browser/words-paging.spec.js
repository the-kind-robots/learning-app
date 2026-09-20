const { test, expect } = require('@playwright/test');

// The requirement counts row elements in the document, so the locator is the
// row class rather than a role: `listitem` also matches the end-of-list
// sentinel and the empty-state row, which are not words.
const rows = (page) => page.locator('li.word-item');
const sentinel = (page) => page.locator('li.word-list__sentinel');

const PAGE_SIZE = 50;
const SEEDED = 130;

// Seeded at the engine level (see README, "Seeding from a spec"): 130 words
// through the add form would be 130 dictionary round-trips, and the list under
// test does not care how a word got there. No reviews — every word then has
// the same retention level, so the retention sort keeps the seeded order.
async function seedWords(page, n) {
  await page.evaluate(async (count) => {
    const now = new Date().toISOString();
    const docs = [];
    for (let i = 0; i < count; i++) {
      const value = 'wort' + String(i).padStart(3, '0');
      docs.push({
        _id: 'vocab:' + value,
        type: 'vocab',
        value,
        translation: [{ lang: 'ru', value: 'слово' + i }],
        created_at: now,
        modified_at: now,
      });
    }
    await db.bulk_docs(db.use('user-db'), docs);
  }, n);
}

// A longer wait than the default: the screen shows "Загружаем..." until the
// app has opened its databases and read the seeded vocabulary, which on a busy
// machine has been measured past the 5 s default.
async function openWords(page) {
  await page.goto('/words');
  await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible({ timeout: 20000 });
}

// Reaching the bottom is a scroll, not a click: the list scrolls inside
// `.vocabulary__list`, so scrolling the last row into view is what a reader
// does and what the observer watches for.
async function scrollToBottom(page) {
  await rows(page).last().scrollIntoViewIfNeeded();
}

const listScrollTop = (page) =>
  page.locator('.vocabulary__list').evaluate((node) => node.scrollTop);

test('the words list renders one page and grows as the reader reaches the bottom', async ({ page }) => {
  await page.goto('/');
  await seedWords(page, SEEDED);
  await openWords(page);

  await expect(rows(page)).toHaveCount(PAGE_SIZE);
  await expect(sentinel(page)).toHaveCount(1);
  await page.screenshot({ path: 'test-results/words-paging/first-page.png', fullPage: false });

  await scrollToBottom(page);
  await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
  // The first page is still there — the next page is appended, not swapped.
  await expect(rows(page).first()).toContainText('wort000');
  await page.screenshot({ path: 'test-results/words-paging/second-page.png', fullPage: false });

  await scrollToBottom(page);
  await expect(rows(page)).toHaveCount(SEEDED);
  // Nothing left to load, so nothing left to observe.
  await expect(sentinel(page)).toHaveCount(0);
  await page.screenshot({ path: 'test-results/words-paging/last-page.png', fullPage: false });
});

test('a search starts again at the first page', async ({ page }) => {
  await page.goto('/');
  await seedWords(page, SEEDED);
  await openWords(page);

  await scrollToBottom(page);
  await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);

  // Matches every seeded word, so a list that kept its loaded count would
  // still show 100.
  await page.getByPlaceholder('Поиск').fill('wort');
  await expect(rows(page)).toHaveCount(PAGE_SIZE);
  // The reader was at the bottom: without the scroll back to the top they
  // would be sitting on the sentinel and the second page would load itself.
  expect(await listScrollTop(page)).toBe(0);
  await page.screenshot({ path: 'test-results/words-paging/after-search.png', fullPage: false });

  await page.getByPlaceholder('Поиск').fill('');
  await expect(rows(page)).toHaveCount(PAGE_SIZE);
});

test('editing a word keeps the rows the reader had loaded', async ({ page }) => {
  await page.goto('/');
  await seedWords(page, SEEDED);
  await openWords(page);

  await scrollToBottom(page);
  await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);

  await rows(page).nth(3).getByRole('button').click();
  await page.getByRole('textbox', { name: 'Перевод' }).fill('исправленный перевод');
  await page.getByRole('button', { name: 'Сохранить' }).click();

  await expect(rows(page).nth(3)).toContainText('исправленный перевод');
  await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
  await page.screenshot({ path: 'test-results/words-paging/after-edit.png', fullPage: false });
});
