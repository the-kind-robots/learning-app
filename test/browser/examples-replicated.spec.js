const { test, expect } = require('@playwright/test');

// Examples replicate with the account (#528, ADR-0020). A device whose user-db
// received a word together with its example sends no example request for it,
// and an example an earlier build kept in device-db moves to user-db at start
// instead of being fetched again.
//
// The suite has no CouchDB, so the pass itself is not run here: the documents
// a pass would bring are written into user-db straight away, and the page is
// reloaded, which is the start of a device that holds them. The pass's own
// path is covered by the node test `an-example-a-pass-brings-answers-its-pair`.

const SENTENCE = 'Die Maus ist klein.';

// Returns the decoded URLs the app asked the example endpoint for, growing as
// it asks. Answers every request with one example.
async function stubExampleEndpoint(page) {
  const requested = [];
  await page.route('**/api/examples*', async (route) => {
    requested.push(decodeURIComponent(route.request().url()));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ value: SENTENCE, translation: 'Мышь маленькая.', structure: [] }),
    });
  });
  return requested;
}

// Resolves once the app has loaded memory, so that the documents seeded next
// are written past a started app, as a pass or an earlier build wrote them.
async function memoryReady(page) {
  await page.waitForFunction(
    () => typeof window.__metrics === 'function' && (window.__metrics().memory || {})['ready-ms'],
  );
}

// Documents written past the app, as a replication pass or an earlier build
// wrote them: raw ones, keys as the app stores them (snake case), and the
// replicated example built by the app's pure `example-doc`, so it has the id
// and body another device of the account writes.
async function seed(page) {
  await page.evaluate(async () => {
    const now = new Date().toISOString();
    const word = (value, ru) => ({
      _id: 'vocab:' + value.toLowerCase(), type: 'vocab', value,
      translation: [{ lang: 'ru', value: ru }], created_at: now, modified_at: now,
    });
    const example = (id, wordId, value) => ({
      _id: id, type: 'example', word_id: wordId, word: wordId.slice(6), value,
      translation: 'перевод', structure: [],
    });
    await db.use('user-db').bulkDocs([
      word('der Hund', 'пёс'),
      word('die Katze', 'кошка'),
      word('die Maus', 'мышь'),
    ]);
    // Fetched on another device of the account, and replicated here.
    const toClj = (o) => cljs.core.js__GT_clj(o, cljs.core.keyword('keywordize-keys'), true);
    await db.insert(db.use('user-db'), adapters.learner.documents.example_doc(
      'vocab:der hund', 'der Hund', null,
      toClj({ value: 'Der Hund bellt.', translation: 'перевод', structure: [] }),
    ));
    // Kept by an earlier build, under a generated id and with its creation time.
    await db.use('device-db').bulkDocs([
      { ...example('6B1F0C2E9A', 'vocab:die katze', 'Die Katze schläft.'), created_at: now },
    ]);
  });
}

// The examples in database `name`, as the word each answers and its sentence.
const examples = (page, name) => page.evaluate(async (name) => {
  const { rows } = await db.use(name).allDocs({ include_docs: true });
  return Object.fromEntries(
    rows.filter((r) => r.doc.type === 'example').map((r) => [r.doc.word_id, r.doc.value]),
  );
}, name);

// The ids of every task device-db has ever held, deleted ones included: the
// change feed keeps a deleted document's id.
const everQueued = (page) => page.evaluate(async () => {
  const { results } = await db.use('device-db').changes({ since: 0 });
  return results.map((r) => r.id).filter((id) => id.startsWith('task:'));
});

// The tasks device-db holds now.
const queued = (page) => page.evaluate(async () => {
  const { rows } = await db.use('device-db').allDocs({ include_docs: true });
  return rows.filter((r) => r.doc.type === 'task').map((r) => r.id);
});

test('a start fetches only the examples the account has none for', async ({ page }) => {
  const requested = await stubExampleEndpoint(page);
  await page.goto('/home');
  await memoryReady(page);
  await seed(page);
  await page.reload();

  // Maus is the one word with no example anywhere, so its fetch is the
  // positive signal that the start's backfill ran.
  await expect.poll(() => examples(page, 'user-db'), { timeout: 20000 }).toEqual({
    'vocab:der hund': 'Der Hund bellt.',
    'vocab:die katze': 'Die Katze schläft.',
    'vocab:die maus': SENTENCE,
  });
  // Every queued fetch has run once no task is left.
  await expect.poll(() => queued(page), { timeout: 20000 }).toEqual([]);

  expect(requested).toHaveLength(1);
  expect(requested[0]).toContain('word=die Maus');
  // The moved example answered Katze before anything was counted, so no
  // fetch for it was ever queued, not merely never sent.
  expect((await everQueued(page)).filter((id) => id.includes('die katze'))).toEqual([]);
  expect((await everQueued(page)).filter((id) => id.includes('der hund'))).toEqual([]);
  expect(await examples(page, 'device-db')).toEqual({});
});
