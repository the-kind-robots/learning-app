// Vocabulary written straight into the user database (see README, "Seeding
// from a spec"), for specs where the words are only the way to what they test.
// Seed on a loaded page, then navigate: memory reads the database on load.
async function seedWords(page, values) {
  await page.evaluate(async (values) => {
    const now = new Date().toISOString();
    const docs = values.map((value) => ({
      _id: 'vocab:' + value, type: 'vocab', value, translation: [{ lang: 'ru', value: 'перевод' }], created_at: now, modified_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, values);
}

// Lessons passed, written straight into the user database: `ages` are how
// many days before now each was passed.
async function seedLessons(page, ages) {
  await page.evaluate(async (ages) => {
    const docs = ages.map((age, index) => ({
      _id: 'lesson-seed-' + index,
      type: 'lesson',
      passed_at: new Date(Date.now() - age * 86400000).toISOString(),
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, ages);
}

module.exports = { seedLessons, seedWords };
