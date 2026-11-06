const { expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { seedExample } = require('./lesson-answer.shared');

// A word with an example: the word is answered (wrong first, when asked), then the example is on screen,
// the word the one trial behind it (#734).

const field = (page) => page.getByRole('textbox', { name: 'Ответ на немецком' });
const check = (page) => page.getByRole('button', { name: 'ПРОВЕРИТЬ' });
const next = (page) => page.getByRole('button', { name: 'ДАЛЕЕ' });
const prompt = (page, text) => page.getByRole('heading', { name: text, exact: true });
const tray = (page) => page.getByRole('group', { name: 'Ваш ответ' });
const bank = (page) => page.getByRole('group', { name: 'Плитки' });

async function answerWordThenShowExample(page, { missFirst, example = { translation: 'Пёс спит в саду.', sentence: 'Der Hund schläft im Garten.' } }) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await seedExample(page, example.translation, example.sentence);
  await page.goto('/lesson');
  if (missFirst) {
    await field(page).fill('die Katze');
    await check(page).click();
    await next(page).click();
  }
  await field(page).fill('der Hund');
  await check(page).click();
  await next(page).click();
  await expect(prompt(page, example.translation)).toBeVisible();
}

// A wheel turned sideways over the strip, one trial back: how a mouse or a trackpad goes back on desktop, where a
// screen changes without a view transition.
async function wheelBack(page) {
  const box = await page.locator('page-strip').boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.wheel(-box.width, 0);
}

module.exports = { answerWordThenShowExample, field, check, prompt, next, tray, bank, wheelBack };
