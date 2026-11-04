const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { openWithAccountKey } = require('./account.shared');
const { placeTiles } = require('./lesson-answer.shared');
const { card, openWord } = require('./word-example.shared');

// The base scenarios on the bundle that ships (#701). The rest of the suite runs the development build and
// leans on its globals (`__metrics`, `db`); the release build renames and drops code the development build
// keeps, and #697 broke there alone. So these touch only what a learner touches, the screen, and the network:
// an account comes from a key in the address, its server copy and the examples endpoint are stubbed.
//
// CI runs them twice: on the development build with the rest of the suite, and on the release bundle as the
// `release` project.

const SENTENCE = 'Der Hund schläft im Garten.';
const SENTENCE_RU = 'Пёс спит в саду.';

// Answers every request for an example with the same one.
async function stubExampleEndpoint(page) {
  await page.route('**/api/examples*', (route) => route.fulfill({
    json: {
      value: SENTENCE,
      translation: SENTENCE_RU,
      structure: [
        { usedForm: 'Hund', dictionaryForm: 'der Hund', translation: 'пёс', wordIndex: 1 },
        { usedForm: 'Garten', dictionaryForm: 'der Garten', translation: 'сад', wordIndex: 4 },
      ],
    },
  }));
}

const exampleOf = (page, theme) => card(page, theme).getByRole('textbox', { name: 'Пример', exact: true });

const answerField = (page) => page.getByRole('textbox', { name: 'Ответ на немецком' });

test.describe('Базовые сценарии на выпускаемой сборке', () => {
  test('ученик с аккаунтом добавляет слово → в окне слова появляется пример', async ({ page }) => {
    await test.step('Дано устройство с аккаунтом', async () => {
      await stubExampleEndpoint(page);
      await openWithAccountKey(page);
    });

    await test.step('Когда он добавляет «der Hund» и открывает его в списке слов', async () => {
      await addWord(page, 'der Hund', 'пёс');
      await page.goto('/words');
      await openWord(page, 'Hund');
    });

    await test.step('Тогда в карточке без темы стоит пример', async () => {
      await expect(exampleOf(page, 'Без темы')).toHaveValue(SENTENCE, { timeout: 15000 });
    });
  });

  test('ученик с аккаунтом добавляет слово и проходит урок → переводит слово, потом собирает пример из плиток', async ({ page }) => {
    await test.step('Дано слово «der Hund» с пришедшим примером', async () => {
      await stubExampleEndpoint(page);
      await openWithAccountKey(page);
      await addWord(page, 'der Hund', 'пёс');
      await page.goto('/words');
      await openWord(page, 'Hund');
      await expect(exampleOf(page, 'Без темы')).toHaveValue(SENTENCE, { timeout: 15000 });
    });

    await test.step('Когда он начинает урок и верно переводит слово', async () => {
      await page.goto('/lesson');
      await answerField(page).fill('der Hund');
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
      await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
    });

    await test.step('Тогда ему предлагают перевести пример', async () => {
      await expect(page.locator('.lesson .prompt')).toHaveText(SENTENCE_RU);
    });

    await test.step('Когда он собирает пример из плиток', async () => {
      await placeTiles(page, SENTENCE);
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
    });

    await test.step('Тогда он видит «Правильно!»', async () => {
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    });
  });

  test('ученик добавляет слово и открывает приложение заново → слово в списке', async ({ page }) => {
    await test.step('Дано добавленное слово «der Hund»', async () => {
      await page.goto('/home');
      await addWord(page, 'der Hund', 'пёс');
    });

    await test.step('Когда он открывает приложение заново на списке слов', async () => {
      await page.goto('/words');
      await page.reload();
    });

    await test.step('Тогда «der Hund» в списке', async () => {
      await expect(page.locator('.word-item .display', { hasText: 'Hund' })).toBeVisible();
    });
  });
});
