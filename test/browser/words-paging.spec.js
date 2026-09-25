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

// Stops `gap` pixels short of the end of the loaded rows and reports, in the
// same task, where the sentinel sits relative to the visible bottom of the
// list. Nothing can re-layout in between — the observer answers in a later
// task — so a positive gap here is the sentinel being off screen at the moment
// the scroll happened.
const scrollToWithin = (page, gap) =>
  page.locator('.vocabulary__list').evaluate((list, gap) => {
    list.scrollTop = list.scrollHeight - list.clientHeight - gap;
    const sentinel = document.querySelector('li.word-list__sentinel');
    return sentinel.getBoundingClientRect().top - list.getBoundingClientRect().bottom;
  }, gap);

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
  // The rows no longer close the dialog on their way in (GH-439), so the save
  // closes it itself.
  await expect(page.locator('dialog.word-edit-dialog')).toHaveCount(0);
  await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
  await page.screenshot({ path: 'test-results/words-paging/after-edit.png', fullPage: false });
});

// GH-439. The margin the observer is built with only widens its root, and the
// root has to be the box that clips the sentinel: left to the default it is the
// viewport, which clips nothing here, so the page was asked for only once the
// reader had scrolled the sentinel into view and could then watch the query
// run.
test('the next page is asked for before the end of the rows is on screen', async ({ page }) => {
  await page.goto('/');
  await seedWords(page, SEEDED);
  await openWords(page);

  await expect(rows(page)).toHaveCount(PAGE_SIZE);

  const gap = await scrollToWithin(page, 150);
  expect(gap).toBeGreaterThan(100);

  await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
  await page.screenshot({ path: 'test-results/words-paging/lookahead.png', fullPage: false });
});

// GH-439, then #494. Reaching the end during the 400 ms the search used to
// wait asked for the next page of the query being replaced, which landed after
// the matching rows and put the whole vocabulary back while the box kept the
// query. The filter now reads memory on the keystroke: the matching rows are
// on screen from their first row before the reader can scroll again, and no
// page of the old query is left to arrive.
test('reaching the end right after a query does not undo it', async ({ page }) => {
  await page.goto('/');
  await seedWords(page, SEEDED);
  await openWords(page);

  await scrollToBottom(page);
  await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);

  // 'wort01' matches wort010..wort019 — ten rows, well under a page, so an
  // unfiltered page arriving afterwards is unmistakable.
  await page.getByPlaceholder('Поиск').fill('wort01');
  await expect(rows(page)).toHaveCount(10);
  expect(await listScrollTop(page)).toBe(0);
  await scrollToBottom(page);

  await expect(page.getByPlaceholder('Поиск')).toHaveValue('wort01');
  await expect(sentinel(page)).toHaveCount(0);

  // Nothing is left to arrive. Auto-waiting says "wait until true" and there
  // is nothing here to wait for, so the only way to establish that no page
  // lands is to let time pass first.
  await page.waitForTimeout(1500);
  await expect(rows(page)).toHaveCount(10);
  await page.screenshot({ path: 'test-results/words-paging/query-survives.png', fullPage: false });
});


// GH-439. Rows used to clear `:words/editing` on their way in, which was
// invisible while only the reader's own actions brought rows and became a
// dialog shutting itself once the sentinel started asking for them.
test('a word stays open while the next page loads', async ({ page }) => {
  await page.goto('/');
  await seedWords(page, SEEDED);
  await openWords(page);

  await rows(page).nth(3).getByRole('button').click();
  const translation = page.getByRole('textbox', { name: 'Перевод' });
  await translation.fill('печатаю прямо сейчас');

  // The dialog is modal, so the list behind it is inert to a click but not to
  // a scroll driven from script — which is what a page arriving in the
  // background looks like from the list's side.
  await scrollToWithin(page, 150);

  await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
  await expect(page.locator('dialog.word-edit-dialog')).toBeVisible();
  await expect(translation).toHaveValue('печатаю прямо сейчас');
  await page.screenshot({ path: 'test-results/words-paging/dialog-survives-page.png', fullPage: false });
});
