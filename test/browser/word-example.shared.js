const { memoryReady } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// The word «Bahnhof» with the examples of its collections, for the specs of the word dialog (GH-598).
// Seeded at the engine level (README, "Seeding from a spec"): the collections get rising creation times, so
// their order is the order given. An example with a `null` collection belongs to no collection.

async function seedWord(page, { active = null, collections = [], examples = [] } = {}) {
  if (active) {
    await page.addInitScript((id) => {
      if (!sessionStorage.getItem('seeded')) {
        localStorage.setItem('active-collection-id', id);
        sessionStorage.setItem('seeded', '1');
      }
    }, active);
  }
  await page.goto('/home');
  await memoryReady(page);
  await addWord(page, 'Bahnhof', 'вокзал');
  await page.evaluate(async ([collections, examples]) => {
    const base = Date.now();
    await db.bulk_docs(db.use('user-db'), collections.map(({ id, name, words }, i) => ({
      _id: id,
      type: 'collection',
      name,
      word_ids: words === undefined ? ['vocab:bahnhof'] : words,
      created_at: new Date(base + i * 1000).toISOString(),
    })));
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    for (const { collection, value, translation } of examples) {
      await db.insert(db.use('user-db'), adapters.learner.documents.example_doc(
        'vocab:bahnhof', 'Bahnhof', collection, toClj({ value, translation }),
      ));
    }
  }, [collections, examples]);
  await page.goto('/words');
  await page.locator('.word-item').first().waitFor({ timeout: 20000 });
}

async function openWord(page, text = 'Bahnhof') {
  await page.locator('.word-item__display', { hasText: text }).first().click();
  await page.getByRole('dialog').waitFor();
}

// What the specs seed: the sentence of each collection, one of them long.
const SEEDED = 'Wir treffen uns um acht am Bahnhof.';
const TRIP = 'Am Bahnhof gibt es eine kleine Bäckerei, in der man morgens frische Brötchen und Kaffee kaufen kann.';
const BARE = 'Wo ist hier der Bahnhof?';

const THEMES = [
  { id: 'collection:bahn', name: 'Reisen/Bahn' },
  { id: 'collection:alltag', name: 'Alltag' },
];

const EXAMPLES = [
  { collection: 'collection:bahn', value: SEEDED, translation: 'Встречаемся в восемь на вокзале.' },
  { collection: 'collection:alltag', value: TRIP, translation: 'На вокзале есть маленькая пекарня.' },
  { collection: null, value: BARE, translation: 'Где здесь вокзал?' },
];

const card = (page, name) => page.getByRole('region', { name });

module.exports = { BARE, EXAMPLES, SEEDED, THEMES, TRIP, card, openWord, seedWord };
