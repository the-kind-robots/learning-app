const { test, expect } = require('@playwright/test');

// Seeded at the engine level (see README, "Seeding from a spec"): a
// collection document carries its own word ids, so no vocabulary is needed
// for the counts. `word_ids` is how the app stores `:word-ids`.
async function seedCollections(page, collections) {
  await page.evaluate(async (collections) => {
    const now = new Date().toISOString();
    const docs = collections.map(([id, name, wordIds]) => ({
      _id: 'collection:' + id, type: 'collection', name, word_ids: wordIds, created_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, collections);
}

async function seedWords(page, values) {
  await page.evaluate(async (values) => {
    const now = new Date().toISOString();
    const docs = values.map((value) => ({
      _id: 'vocab:' + value, type: 'vocab', value, translation: [{ lang: 'ru', value: 'перевод' }], created_at: now, modified_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, values);
}

async function collectionNames(page) {
  return page.evaluate(async () => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    const found = await db.find(db.use('user-db'), toClj({ selector: { type: 'collection' } }));
    return cljs.core.clj__GT_js(cljs.core.map(kw('name'), cljs.core.get(found, kw('docs'))));
  });
}

const course = [
  ['kurs', 'Kurs', ['vocab:a', 'vocab:b']],
  ['k1', 'Kurs / Kapitel 1', ['vocab:b', 'vocab:c']],
  ['k2', 'Kurs / Kapitel 2', ['vocab:d']],
  ['gram', 'Grammatik / Konnektoren', ['vocab:e', 'vocab:f']],
];

test('collections with a slash fold into folder tiles whose header counts the union', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, course);

  await page.getByRole('link', { name: 'Открыть наборы' }).click();

  // The header's name is its text: the folder key and the union count —
  // a, b, c, d with b once.
  await expect(page.getByRole('button', { name: 'Kurs 4', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Kapitel 1 2', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Kapitel 2 1', exact: true })).toBeVisible();
  // No `Grammatik` document: the header still counts the children.
  await expect(page.getByRole('button', { name: 'Grammatik 2', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Konnektoren 2', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Всё подряд 0', exact: true })).toBeVisible();
  // A child is never a tile of its own.
  await expect(page.getByRole('button', { name: /Kurs \/ Kapitel/ })).toHaveCount(0);
});

test('tapping a folder header with no document creates the parent collection and opens it', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, course);
  await page.getByRole('link', { name: 'Открыть наборы' }).click();

  await page.getByRole('button', { name: 'Grammatik 2', exact: true }).click();

  await expect(page.getByRole('heading', { name: 'Grammatik' })).toBeVisible();
  expect(await collectionNames(page)).toContain('Grammatik');
});

test('tapping a row opens that collection', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, course);
  await page.getByRole('link', { name: 'Открыть наборы' }).click();

  await page.getByRole('button', { name: 'Kapitel 1 2', exact: true }).click();

  await expect(page.getByRole('heading', { name: 'Kurs / Kapitel 1' })).toBeVisible();
});

test('the words list on a parent shows its children\'s words', async ({ page }) => {
  await page.goto('/');
  await seedWords(page, ['a', 'b', 'c', 'd', 'e', 'f']);
  await seedCollections(page, course);
  await page.getByRole('link', { name: 'Открыть наборы' }).click();
  await page.getByRole('button', { name: 'Kurs 4', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Kurs' })).toBeVisible();

  await page.goto('/words');

  await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible();
  // Kurs holds a and b; its chapters add c and d; e and f are Grammatik's.
  for (const value of ['a', 'b', 'c', 'd']) {
    await expect(page.getByText(value, { exact: true })).toBeVisible();
  }
  await expect(page.getByText('e', { exact: true })).toHaveCount(0);
  await expect(page.getByText('f', { exact: true })).toHaveCount(0);
});
