const { expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// The big bottom button stands in one place on home, the words list and the lesson (#767).

// The button's box once it has settled: two reads a frame apart that agree.
async function settledBox(button) {
  await expect(button).toBeVisible();
  let box;
  await expect.poll(async () => {
    const previous = box;
    box = await button.boundingBox();
    return JSON.stringify(previous) === JSON.stringify(box);
  }).toBe(true);
  return box;
}

// Opens the words list from home, starts a lesson from it and answers wrong; the main button's box on each.
async function mainButtonBoxes(page) {
  const start = page.getByRole('button', { name: 'НАЧАТЬ УРОК' });
  const boxes = {};
  boxes['главная «НАЧАТЬ УРОК»'] = await settledBox(start);
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page.getByRole('button', { name: 'Закрыть' })).toBeVisible();
  boxes['слова «НАЧАТЬ УРОК»'] = await settledBox(start);
  await start.click();
  const check = page.getByRole('button', { name: 'ПРОВЕРИТЬ' });
  boxes['урок «ПРОВЕРИТЬ»'] = await settledBox(check);
  await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('Falsch');
  await check.click();
  boxes['урок «ДАЛЕЕ»'] = await settledBox(page.getByRole('button', { name: 'ДАЛЕЕ' }));
  return boxes;
}

async function twoWordsOnHome(page) {
  await page.goto('/home');
  await addWord(page, 'Haus', 'дом');
  await addWord(page, 'Hund', 'собака');
}

module.exports = { mainButtonBoxes, twoWordsOnHome };
