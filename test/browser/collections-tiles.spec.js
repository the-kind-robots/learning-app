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

  await page.getByRole('button', { name: 'Открыть наборы' }).click();

  // The header's name is its text: the folder key and the union count —
  // a, b, c, d with b once.
  await expect(page.getByRole('button', { name: 'Kurs 4', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Kapitel 1 2', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Kapitel 2 1', exact: true })).toBeVisible();
  // No `Grammatik` document: the header is a label with the children's
  // union, not a target.
  await expect(page.getByRole('heading', { name: 'Grammatik 2', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /Grammatik/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Konnektoren 2', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Всё подряд 0', exact: true })).toBeVisible();
  // A child is never a tile of its own.
  await expect(page.getByRole('button', { name: /Kurs \/ Kapitel/ })).toHaveCount(0);
});

// All three targets are buttons, not divs wearing `role="button"`: the role
// alone announces a control the keyboard cannot reach. Tab is the only honest
// test of that — `.focus()` does nothing on an unfocusable element and would
// pass either way — so this walks focus and keeps what it lands on.
async function focusedStop(page) {
  return page.evaluate(() => {
    const el = document.activeElement;
    if (!el || !el.closest('.masonry')) return null;
    // A target carries its collection id; a ✕ is named by its label.
    return el.getAttribute('data-collection-id') || el.getAttribute('aria-label');
  });
}

async function tabUntil(page, locator, key = 'Tab') {
  const reached = [];
  for (let step = 0; step < 40; step += 1) {
    await page.keyboard.press(key);
    const stop = await focusedStop(page);
    if (stop) reached.push(stop);
    if (await locator.evaluate((el) => el === document.activeElement)) break;
  }
  return reached;
}

test('a keyboard reaches every target, each ✕ right after its own', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, course.concat([['solo', 'Solo', ['vocab:a']]]));

  await page.getByRole('button', { name: 'Открыть наборы' }).click();
  // Last in the reading order, so the walk passes the others on the way.
  const tile = page.getByRole('button', { name: 'Solo 1', exact: true });
  await expect(tile).toBeVisible();

  // «Всё подряд», the Grammatik folder's one row, the Kurs header and its
  // two rows, then the plain tile: every target in reading order, and every
  // named collection's ✕ right after it.
  expect(await tabUntil(page, page.getByRole('button', { name: 'Удалить набор «Solo»' }))).toEqual([
    'main',
    'collection:gram', 'Удалить набор «Konnektoren»',
    'collection:kurs', 'Удалить набор «Kurs»',
    'collection:k1', 'Удалить набор «Kapitel 1»',
    'collection:k2', 'Удалить набор «Kapitel 2»',
    'collection:solo', 'Удалить набор «Solo»',
  ]);

  await page.keyboard.press('Shift+Tab');
  await expect(tile).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Solo', exact: true })).toBeVisible();
});

test('the ✕ shows for keyboard focus, deletes on Enter and hands focus to the neighbour', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, course.concat([['solo', 'Solo', ['vocab:a']]]));
  await page.getByRole('button', { name: 'Открыть наборы' }).click();
  const tile = page.getByRole('button', { name: 'Solo 1', exact: true });
  await tile.click();
  await expect(page.getByRole('heading', { name: 'Solo', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Открыть наборы' }).click();

  // The active collection is the current one, and only it.
  await expect(tile).toHaveAttribute('aria-current', 'true');
  await expect(page.locator('.masonry [aria-current]')).toHaveCount(1);

  // Hidden and untappable until keyboard focus comes in.
  const close = page.getByRole('button', { name: 'Удалить набор «Solo»' });
  await expect(close).toHaveCSS('opacity', '0');
  await expect(close).toHaveCSS('pointer-events', 'none');

  await tabUntil(page, tile);
  await expect(close).toHaveCSS('opacity', '1');
  await expect(close).toHaveCSS('pointer-events', 'auto');
  await expect(tile.locator('.tile__count')).toHaveCSS('opacity', '0');
  await page.keyboard.press('Tab');
  await expect(close).toBeFocused();

  // Last on the screen: focus falls back to the target before it, the Kurs
  // folder's last row.
  await page.keyboard.press('Enter');
  await expect(tile).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Kapitel 2 1', exact: true })).toBeFocused();
  await expect(page.getByRole('status')).toHaveText('Набор «Solo» удалён');
  expect(await collectionNames(page)).not.toContain('Solo');
  // The active collection is gone, so «Всё подряд» is current.
  await expect(page.getByRole('button', { name: 'Всё подряд 0', exact: true })).toHaveAttribute('aria-current', 'true');

  // A folder's parent leaves a label behind; its first row takes the focus.
  await tabUntil(page, page.getByRole('button', { name: 'Удалить набор «Kurs»' }), 'Shift+Tab');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('button', { name: 'Kurs 4', exact: true })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Kurs 3', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Kapitel 1 2', exact: true })).toBeFocused();
  await expect(page.getByRole('status')).toHaveText('Набор «Kurs» удалён');
});

test('a folder header with no document is a label; creating the parent through «+» makes it the tile', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, course);
  await page.getByRole('button', { name: 'Открыть наборы' }).click();

  // The label takes no tap: still on the themes screen, no document written.
  await page.getByRole('heading', { name: 'Grammatik 2', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Наборы' })).toBeVisible();
  expect(await collectionNames(page)).not.toContain('Grammatik');

  // Creating the parent is the user's job: the «+» prompt.
  page.once('dialog', (dialog) => dialog.accept('Grammatik'));
  await page.getByRole('button', { name: 'Новый набор' }).click();

  // The header is now the collection, with the union of its children.
  await expect(page.getByRole('button', { name: 'Grammatik 2', exact: true })).toBeVisible();
  expect(await collectionNames(page)).toContain('Grammatik');
});

test('renaming a collection to a name already taken is refused, so one tile per name', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, course);
  await page.getByRole('button', { name: 'Открыть наборы' }).click();
  await page.getByRole('button', { name: 'Kurs 4', exact: true }).click();

  // The heading is the inline rename (contenteditable plaintext-only, which
  // fill() does not recognise — typed instead); Enter submits it.
  const heading = page.getByRole('heading', { name: 'Kurs' });
  await expect(heading).toBeVisible();
  await heading.click();
  await page.keyboard.press('ControlOrMeta+a');
  await page.keyboard.type(' kurs / kapitel 1 ');
  await page.keyboard.press('Enter');

  // Refused: the heading reverts, and the documents still carry one name each.
  await expect(page.getByRole('heading', { name: 'Kurs', exact: true })).toBeVisible();
  const names = await collectionNames(page);
  expect(names.filter((n) => n.trim().toLowerCase() === 'kurs / kapitel 1')).toEqual(['Kurs / Kapitel 1']);
  expect(names.filter((n) => n.trim().toLowerCase() === 'kurs')).toEqual(['Kurs']);

  await page.getByRole('button', { name: 'Открыть наборы' }).click();
  await expect(page.getByRole('button', { name: 'Kurs 4', exact: true })).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'Kapitel 1 2', exact: true })).toHaveCount(1);
});

// The issue's repro (#460): the collections icon is clicked while the caret
// is still in the heading. The click's mousedown takes focus, so the rename
// starts on that blur and the themes screen opens before its write lands.
test('a rename left in the heading shows on the themes screen opened from it', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, [['xa', 'xxx, aaa', []]]);
  await page.getByRole('button', { name: 'Открыть наборы' }).click();
  await page.getByRole('button', { name: 'xxx, aaa 0', exact: true }).click();

  const heading = page.getByRole('heading', { name: 'xxx, aaa' });
  await expect(heading).toBeVisible();
  await heading.click();
  await page.keyboard.press('ControlOrMeta+a');
  await page.keyboard.type('xxx / aaa');
  await page.getByRole('button', { name: 'Открыть наборы' }).click();

  await expect(page.getByRole('heading', { name: 'xxx 0', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'aaa 0', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /xxx, aaa/ })).toHaveCount(0);
});

test('tapping a row opens that collection', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, course);
  await page.getByRole('button', { name: 'Открыть наборы' }).click();

  await page.getByRole('button', { name: 'Kapitel 1 2', exact: true }).click();

  await expect(page.getByRole('heading', { name: 'Kurs / Kapitel 1' })).toBeVisible();
});

test('the words list on a parent shows its children\'s words', async ({ page }) => {
  await page.goto('/');
  await seedWords(page, ['a', 'b', 'c', 'd', 'e', 'f']);
  await seedCollections(page, course);
  await page.getByRole('button', { name: 'Открыть наборы' }).click();
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
