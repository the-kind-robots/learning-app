const { test, expect } = require('./fixtures');
const { mainButtonBoxes, twoWordsOnHome } = require('./main-button-place.shared');

test('ученик на компьютере идёт с главной через наборы и список слов в урок → главная кнопка везде одного размера и на одном месте', async ({ page }) => {
  let boxes;

  await test.step('Дано два слова на главной', async () => {
    await twoWordsOnHome(page);
  });

  await test.step('Когда он открывает и закрывает наборы, открывает список слов, начинает из него урок и отвечает неверно', async () => {
    boxes = await mainButtonBoxes(page);
  });

  await test.step('Тогда «ДОБАВИТЬ НАБОР», «НАЧАТЬ УРОК», «ПРОВЕРИТЬ» и «ДАЛЕЕ» там же и такие же, как на главной', async () => {
    const [onHome] = Object.values(boxes);
    expect(boxes).toEqual(Object.fromEntries(Object.keys(boxes).map((screen) => [screen, onHome])));
  });
});
