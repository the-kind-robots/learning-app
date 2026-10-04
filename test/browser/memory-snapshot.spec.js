const path = require('path');
const { test, expect } = require('@playwright/test');

// A repeat start takes memory from a snapshot in the Cache API and catches up
// from its feed positions (#508, ADR-0018). A snapshot that fails a check is
// deleted, and every document is read.

const rows = (page) => page.locator('.word-item');

// Memory is loaded when the development build's metrics say so; they also
// say where memory came from.
async function memoryFrom(page) {
  await page.waitForFunction(
    () => typeof window.__metrics === 'function' && window.__metrics().memory['ready-ms'],
    null,
    { timeout: 60000 },
  );
  return page.evaluate(() => window.__metrics().memory.from);
}

// The stored snapshot as text, or '' when there is none.
const storedSnapshot = (page) => page.evaluate(async () => {
  const cache = await caches.open('learner-memory');
  const response = await cache.match('/learner-memory/snapshot');
  return response ? response.text() : '';
});

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

// The first start reads every document and stores a snapshot. Then words are
// seeded and the page reloaded: that start takes the snapshot, catches up
// with the words, and stores a snapshot that holds them.
async function withSnapshot(page) {
  await page.goto('/home');
  expect(await memoryFrom(page)).toBe('databases');
  await expect.poll(() => storedSnapshot(page)).not.toBe('');
  await seedWords(page, 30);
  await page.reload();
  await memoryFrom(page);
  await expect.poll(() => storedSnapshot(page)).toContain('vocab:wort1029');
}

// Rebuilds user-db as if it had lost its last change: every change but the
// last, under the same sequences, with the same marker, and then `other`, a
// document nobody had, when it is given. Runs on a page of the same origin
// where the app is not running, with PouchDB loaded by itself. Resolves with
// `{lastSeq, rebuiltSeq}`: user-db's sequence before and after.
async function loseLastChange(page, other) {
  await page.goto('/favicon.ico');
  await page.addScriptTag({ path: path.join(__dirname, '../../node_modules/pouchdb/dist/pouchdb.min.js') });
  return page.evaluate(async (other) => {
    const user = new PouchDB('user-db');
    const marker = await user.get('_local/database-marker');
    const { results } = await user.changes({ include_docs: true });
    const lastSeq = results[results.length - 1].seq;
    await user.destroy();
    const rebuilt = new PouchDB('user-db');
    for (const { doc } of results.slice(0, -1)) {
      await rebuilt.bulkDocs([doc], { new_edits: false });
    }
    if (other) {
      await rebuilt.put(other);
    }
    await rebuilt.put({ _id: '_local/database-marker', marker: marker.marker });
    const rebuiltSeq = (await rebuilt.info()).update_seq;
    await rebuilt.close();
    return { lastSeq, rebuiltSeq };
  }, other);
}

async function search(page, text) {
  await page.getByPlaceholder('Поиск').fill(text);
}

test('a repeat start takes memory from the snapshot, with what was stored since', async ({ page }) => {
  await withSnapshot(page);
  // Stored after the snapshot, past the app, as another tab or a pull does.
  await page.evaluate(async () => {
    const now = new Date().toISOString();
    await db.use('user-db').put({ _id: 'vocab:neu', type: 'vocab', value: 'neu', translation: [{ lang: 'ru', value: 'новый' }], created_at: now, modified_at: now });
  });

  await page.goto('/words');
  expect(await memoryFrom(page)).toBe('snapshot');
  await search(page, 'neu');
  await expect(rows(page)).toHaveCount(1);
  await search(page, 'Wort1029');
  await expect(rows(page)).toHaveCount(1);
});

test('a snapshot of another database is dropped, and every document is read', async ({ page }) => {
  await withSnapshot(page);
  // user-db is not the one the snapshot was taken from.
  await page.evaluate(async () => {
    const local = await db.use('user-db').get('_local/database-marker');
    await db.use('user-db').put({ ...local, marker: 'another' });
  });

  await page.goto('/words');
  expect(await memoryFrom(page)).toBe('databases');
  await search(page, 'Wort1029');
  await expect(rows(page)).toHaveCount(1);
  await expect.poll(() => storedSnapshot(page)).toContain('"another"');
});

test('a snapshot ahead of its database is dropped, and every document is read', async ({ page }) => {
  await withSnapshot(page);
  const { lastSeq, rebuiltSeq } = await loseLastChange(page, null);
  expect(rebuiltSeq).toBe(lastSeq - 1);

  await page.goto('/words');
  expect(await memoryFrom(page)).toBe('databases');
  await search(page, 'Wort1028');
  await expect(rows(page)).toHaveCount(1);
  await search(page, 'Wort1029');
  await expect(rows(page)).toHaveCount(0);
});

test('a snapshot of a database that lost its last change and stored another is dropped', async ({ page }) => {
  await withSnapshot(page);
  const now = new Date().toISOString();
  const { lastSeq, rebuiltSeq } = await loseLastChange(page, {
    _id: 'vocab:fremd', type: 'vocab', value: 'fremd', translation: [{ lang: 'ru', value: 'чужой' }], created_at: now, modified_at: now,
  });
  expect(rebuiltSeq).toBe(lastSeq);

  await page.goto('/words');
  expect(await memoryFrom(page)).toBe('databases');
  await search(page, 'fremd');
  await expect(rows(page)).toHaveCount(1);
  await search(page, 'Wort1029');
  await expect(rows(page)).toHaveCount(0);
});
