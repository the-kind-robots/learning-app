const { test, expect } = require('./fixtures');
const { mainButtonBoxes, settledBox, twoWordsOnHome } = require('./main-button-place.shared');

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

test('ученик на компьютере листает наборы, которые не помещаются на экран → листаются только наборы, «ДОБАВИТЬ НАБОР» там же и такая же, как «НАЧАТЬ УРОК»', async ({ page }) => {
  const add = page.getByRole('button', { name: 'ДОБАВИТЬ НАБОР' });
  let onHome;

  await test.step('Дано два слова и сорок наборов', async () => {
    await twoWordsOnHome(page);
    await page.evaluate(async () => {
      const now = new Date().toISOString();
      const docs = Array.from({ length: 40 }, (_, i) => ({
        _id: 'collection:' + i, type: 'collection', name: 'Thema ' + String(i).padStart(2, '0'), word_ids: [], created_at: now,
      }));
      await db.bulk_docs(db.use('user-db'), docs);
    });
    await page.goto('/home');
    onHome = await settledBox(page.getByRole('button', { name: 'НАЧАТЬ УРОК' }));
  });

  await test.step('Когда он открывает наборы и листает их колесом вниз', async () => {
    await page.getByRole('button', { name: 'Открыть наборы' }).click();
    await settledBox(add);
    await page.mouse.move(640, 300);
    await page.mouse.wheel(0, 2000);
  });

  await test.step('Тогда первые наборы ушли вверх, страница стоит, а «ДОБАВИТЬ НАБОР» там же и такая же, как на главной', async () => {
    await expect(page.getByRole('button', { name: 'Thema 00 0', exact: true })).not.toBeInViewport();
    // A page that scrolled would show its scrollbar beside the footer and, in a browser where a scrollbar takes
    // room, narrow the button.
    expect(await page.evaluate(() => document.scrollingElement.scrollTop)).toBe(0);
    expect(await settledBox(add)).toEqual(onHome);
  });
});
