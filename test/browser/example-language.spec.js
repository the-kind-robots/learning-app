const { test, expect, memoryReady } = require('./fixtures');
const { becomeAccount } = require('./account.shared');
const { BARE, card, openWord, seedWord } = require('./word-example.shared');

// Examples follow the learner's language (#475): a request carries it, an
// example remembers it, and the lesson, the word dialog and the backfill see
// the examples of the learner's language only. An example stored before
// examples carried a language is Russian. The endpoint is stubbed with
// `page.route`.

const settings = (page) => page.getByRole('dialog', { name: /^(Настройки|Settings)$/ });
const wordMark = (page) => page.getByRole('button', { name: /^Sprecha/ });

async function pickLanguage(page, name) {
  await wordMark(page).click();
  await settings(page).getByRole('radio', { name }).click();
  await wordMark(page).click();
  await expect(settings(page)).toHaveCount(0);
}

// The languages and words the page asks the endpoint about; never answered, so
// each word is asked once until the page aborts it.
async function recordRequests(page) {
  const requested = [];
  await page.route('**/api/examples*', (route) => {
    const params = new URL(route.request().url()).searchParams;
    requested.push({ language: params.get('language'), word: params.get('word') });
  });
  return requested;
}

async function openWithAccount(page) {
  const requested = await recordRequests(page);
  await page.goto('/home');
  await memoryReady(page);
  await becomeAccount(page);
  await memoryReady(page);
  return requested;
}

// The word «Hund», with a Russian example stored before examples carried a language when `example` is set.
async function seedHund(page, { example = false } = {}) {
  await page.evaluate(async (example) => {
    const now = new Date().toISOString();
    await db.use('user-db').put({
      _id: 'vocab:hund', type: 'vocab', value: 'Hund',
      translation: [{ lang: 'ru', value: 'собака' }], created_at: now, modified_at: now,
    });
    if (example) {
      const kw = cljs.core.keyword;
      await db.insert(db.use('user-db'), adapters.learner.documents.example_doc(
        'vocab:hund', 'Hund', null,
        cljs.core.js__GT_clj({ value: 'Der Hund bellt laut.', translation: 'Собака громко лает.' }, kw('keywordize-keys'), true),
      ));
    }
  }, example);
}

const sentence = (region) => region.getByLabel(/^(Пример|Example)$/);

// A plain wait, named so the reason is visible at the call site: the assertion
// is that nothing is asked, and nothing being asked takes time to establish.
const nothingHappensFor = (page, ms) => page.waitForTimeout(ms);

test.describe('Язык примеров', () => {
  test('ученик на английском получает слово без примера → запрос примера несёт английский', async ({ page }) => {
    let requested;
    await test.step('Дано устройство с аккаунтом, ученик выбрал English', async () => {
      requested = await openWithAccount(page);
      await pickLanguage(page, 'English');
    });

    await test.step('Когда в память приходит слово без примера', async () => {
      await seedHund(page);
    });

    await test.step('Тогда пример этого слова просится на английском', async () => {
      await expect.poll(() => requested, { timeout: 15000 }).toContainEqual({ language: 'en', word: 'Hund' });
    });
  });

  test('у слова есть русский пример, ученик переключается на английский → устройство просит английский пример', async ({ page }) => {
    let requested;
    await test.step('Дано устройство с аккаунтом и слово с русским примером', async () => {
      requested = await openWithAccount(page);
      await seedHund(page, { example: true });
      await nothingHappensFor(page, 3000);
      expect(requested).toHaveLength(0);
    });

    await test.step('Когда ученик выбирает English', async () => {
      await pickLanguage(page, 'English');
    });

    await test.step('Тогда пример слова просится на английском', async () => {
      await expect.poll(() => requested, { timeout: 15000 }).toEqual([{ language: 'en', word: 'Hund' }]);
    });
  });

  test('ученик переключается на английский и обратно → в окне слова русский пример пропадает и возвращается', async ({ page }) => {
    await test.step('Дано слово с русским примером без темы', async () => {
      await seedWord(page, { examples: [{ collection: null, value: BARE, translation: 'Где здесь вокзал?' }] });
    });

    await test.step('Когда ученик выбирает English и открывает слово', async () => {
      await pickLanguage(page, 'English');
      await openWord(page);
    });

    await test.step('Тогда карточка пуста', async () => {
      await expect(sentence(card(page, 'No theme'))).toHaveValue('');
    });

    await test.step('Когда он закрывает окно, выбирает Русский и открывает слово снова', async () => {
      await page.keyboard.press('Escape');
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await pickLanguage(page, 'Русский');
      await openWord(page);
    });

    await test.step('Тогда он видит прежний русский пример', async () => {
      await expect(sentence(card(page, 'Без темы'))).toHaveValue(BARE);
    });
  });
});
