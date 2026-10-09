const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// The learner's data in memory stays a projection of PouchDB (#494,
// ADR-0016). A screen reads it when opened and does not follow it while open:
// the app's own write shows at once, another tab's and a replicated one on
// the next opening. The app opens on a splash until the words and collections
// are read, and a screen left does not come back.

const rows = (page) => page.locator('.word-item');
const homeHeading = (page) => page.getByRole('heading', { name: 'Главная' });

// Memory is loaded when the development build's metrics say so.
const memoryReady = (page) => page.waitForFunction(
  () => typeof window.__metrics === 'function' && window.__metrics().memory['ready-ms'],
  null,
  { timeout: 60000 },
);

test('a word just added is on the words screen the moment it opens', async ({ page }) => {
  await page.goto('/home');
  await memoryReady(page);
  await addWord(page, 'Haus', 'дом');

  const shown = await page.evaluate(() => new Promise((resolve) => {
    requestAnimationFrame(() => resolve(
      [...document.querySelectorAll('.word-item__value')].map((n) => n.textContent),
    ));
    document.querySelector('#home-words-button').click();
  }));
  expect(shown).toEqual(['Haus']);
});

async function reopenWords(page) {
  await page.getByRole('button', { name: 'Закрыть' }).click();
  await expect(page).toHaveURL(/\/home$/);
  await page.getByRole('button', { name: 'Список слов' }).click();
}

// Establishing that something never happens takes letting time pass
// (test/browser/README.md, "Conventions").
const nothingHappensFor = (page, ms) => page.waitForTimeout(ms);

test('a word added in another tab shows on the words screen when it is opened again', async ({ context }) => {
  const reader = await context.newPage();
  await reader.goto('/home');
  await memoryReady(reader);
  await addWord(reader, 'Haus', 'дом');
  await reader.getByRole('button', { name: 'Список слов' }).click();
  await expect(rows(reader)).toHaveCount(1);

  const writer = await context.newPage();
  await writer.goto('/home');
  await memoryReady(writer);
  await addWord(writer, 'Hund', 'пёс');

  // The open list stays as it was; memory has the word from the feed.
  await nothingHappensFor(reader, 1000);
  await expect(rows(reader)).toHaveCount(1);
  await reopenWords(reader);
  await expect(rows(reader).filter({ hasText: 'Hund' })).toBeVisible();
  await expect(rows(reader)).toHaveCount(2);
});

test('a word replicated from another device shows on the words screen when it is opened again', async ({ page }) => {
  await page.goto('/home');
  await memoryReady(page);
  await addWord(page, 'Haus', 'дом');
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(rows(page)).toHaveCount(1);

  // Another device's database, replicated into this one the way a sync pull
  // writes: through PouchDB's replicator, not through any path of the app.
  await page.evaluate(async () => {
    const other = db.use('another-device');
    await other.put({
      _id: 'vocab:katze', type: 'vocab', value: 'Katze',
      translation: [{ lang: 'ru', value: 'кошка' }],
      created_at: new Date().toISOString(), modified_at: new Date().toISOString(),
    });
    await other.constructor.replicate(other, db.use('user-db'));
    await other.destroy();
  });

  await nothingHappensFor(page, 1000);
  await expect(rows(page)).toHaveCount(1);
  await reopenWords(page);
  await expect(rows(page).filter({ hasText: 'Katze' })).toBeVisible();
  await expect(rows(page)).toHaveCount(2);
});

test('leaving the words screen at once does not bring it back over home', async ({ page }) => {
  await page.goto('/home');
  await memoryReady(page);
  await addWord(page, 'Haus', 'дом');

  // #486: the close in the same task as the open, before anything the open
  // started could land.
  await page.evaluate(() => {
    document.querySelector('#home-words-button').click();
    document.querySelector('button[aria-label="Закрыть"]').click();
  });
  await expect(homeHeading(page)).toBeAttached();
  await expect(page).toHaveURL(/\/home$/);

  // Establishing that something never happens takes letting time pass
  // (test/browser/README.md, "Conventions").
  await page.waitForTimeout(1500);
  await expect(rows(page)).toHaveCount(0);
  await expect(page).toHaveURL(/\/home$/);
  await expect(homeHeading(page)).toBeAttached();
});

// Watches the page from before the app boots: whether either empty state of
// the words screen was ever on display — a claim shown for a moment is
// caught.
const watchStartup = `
  window.__claimed = [];
  new MutationObserver(() => {
    if (!document.body) return;
    for (const text of ['Слов пока нет', 'Ничего не найдено']) {
      if (document.body.textContent.includes(text) && !window.__claimed.includes(text)) {
        window.__claimed.push(text);
      }
    }
  }).observe(document, { childList: true, subtree: true, characterData: true });
`;

async function seedWords(page, n) {
  await page.evaluate(async (n) => {
    const now = new Date().toISOString();
    const docs = [];
    for (let i = 0; i < n; i++) {
      docs.push({ _id: 'vocab:wort' + (1000 + i), type: 'vocab', value: 'Wort' + (1000 + i), translation: [{ lang: 'ru', value: 'слово' }], created_at: now, modified_at: now });
    }
    await db.use('user-db').bulkDocs(docs);
  }, n);
}

test('the app opens on the screen asked for with its words, never on an empty state', async ({ page }) => {
  await page.goto('/home');
  await memoryReady(page);
  await seedWords(page, 2000);

  await page.addInitScript(watchStartup);
  await page.goto('/words');
  await expect(rows(page).first()).toBeVisible({ timeout: 30000 });
  await expect(page).toHaveURL(/\/words$/);
  expect(await page.evaluate(() => window.__claimed)).toEqual([]);
});

test('an empty vocabulary and a filter with no match still tell apart', async ({ page }) => {
  await page.goto('/words');
  await memoryReady(page);
  await expect(page.getByText('Слов пока нет')).toBeVisible();
  await expect(page.getByPlaceholder('Поиск')).toHaveCount(0);

  await page.getByRole('button', { name: 'Добавить слово' }).click();
  await addWord(page, 'Haus', 'дом');
  await page.getByRole('button', { name: 'Список слов' }).click();
  await page.getByPlaceholder('Поиск').fill('zzz');
  await expect(page.getByText('Ничего не найдено')).toBeVisible();
  await expect(page.getByText('Слов пока нет')).toHaveCount(0);
  await expect(page.getByPlaceholder('Поиск')).toHaveValue('zzz');
});


// The active collection is the one memory holds under the remembered id
// (collections-data-model). An id it holds nothing under — the collection
// was deleted on another device — acts as «Всё подряд» on every path, and
// nothing clears it.
test('a remembered collection that is gone acts as «Всё подряд» everywhere', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('active-collection-id', 'collection:gone'));
  await page.goto('/home');
  await memoryReady(page);
  await expect(homeHeading(page)).toBeVisible();

  await addWord(page, 'Haus', 'дом');
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(rows(page)).toHaveCount(1);

  let prompt = null;
  page.once('dialog', (dialog) => { prompt = dialog.message(); dialog.dismiss(); });
  await page.locator('.word-item__display').first().click();
  await page.locator('.word-edit-dialog__delete').click();
  await expect.poll(() => prompt).toBe('Удалить «Haus» окончательно?');
  await page.locator('.word-edit-dialog__cancel').click();

  await page.getByRole('button', { name: 'Закрыть' }).click();
  await page.locator('.home__lesson-button').click();
  await expect(page.locator('.lesson__prompt')).toBeVisible();

  await page.goto('/collections');
  await expect(page.getByRole('button', { name: 'Всё подряд 1', exact: true })).toHaveAttribute('aria-current', 'true');
  expect(await page.evaluate(() => localStorage.getItem('active-collection-id'))).toBe('collection:gone');
});

