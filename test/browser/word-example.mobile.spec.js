const { test, expect } = require('./fixtures');
const { EXAMPLES, THEMES, card, openWord, seedWord } = require('./word-example.shared');

// GH-598: the word dialog on a phone: the sheet fits the screen, the cards are side by side and as wide
// as the design says, and the footer is one row.

test.describe('Окно слова на телефоне', () => {
  test('пользователь открывает слово → окно помещается в экран, карточки по 290 px в ряд, кнопки внизу в один ряд', async ({ page }) => {
    await test.step('Дано слово в двух темах и пример без темы', async () => {
      await seedWord(page, { collections: THEMES, examples: EXAMPLES });
    });

    await test.step('Когда он открывает слово', async () => {
      await openWord(page);
    });

    await test.step('Тогда окно внутри экрана, а страница не прокручивается вбок', async () => {
      const box = await page.getByRole('dialog').boundingBox();
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(390);
      expect(box.y + box.height).toBeLessThanOrEqual(844);
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
    });

    await test.step('Тогда карточки одной ширины 290 px, стоят в ряд', async () => {
      const boxes = await page.getByRole('region').evaluateAll((nodes) => nodes.map((n) => n.getBoundingClientRect()));
      expect(boxes).toHaveLength(3);
      for (const b of boxes) expect(Math.round(b.width)).toBe(290);
      expect(new Set(boxes.map((b) => Math.round(b.y))).size).toBe(1);
    });

    await test.step('Тогда «Удалить слово», «Сбросить» и «Сохранить» в одном ряду, внутри окна', async () => {
      const dialog = await page.getByRole('dialog').boundingBox();
      const buttons = [
        await page.getByRole('button', { name: 'Удалить слово' }).boundingBox(),
        await page.getByRole('button', { name: 'Сбросить' }).boundingBox(),
        await page.getByRole('button', { name: 'Сохранить' }).boundingBox(),
      ];
      expect(new Set(buttons.map((b) => Math.round(b.y + b.height / 2))).size).toBe(1);
      for (const b of buttons) {
        expect(b.x).toBeGreaterThanOrEqual(dialog.x);
        expect(b.x + b.width).toBeLessThanOrEqual(dialog.x + dialog.width);
      }
    });
  });

  test('пользователь открывает слово в одной теме → карточка во всю ширину окна, поля по бокам равны', async ({ page }) => {
    await test.step('Дано слово в одной теме', async () => {
      await seedWord(page, { collections: [THEMES[0]], examples: [EXAMPLES[0]] });
    });

    await test.step('Когда он открывает слово', async () => {
      await openWord(page);
    });

    await test.step('Тогда карточка тянется по окну, слева и справа поля по 28 px', async () => {
      const dialog = await page.getByRole('dialog').boundingBox();
      const one = await card(page, 'Reisen / Bahn').boundingBox();
      expect(Math.round(one.x - dialog.x)).toBe(28);
      expect(Math.round(dialog.x + dialog.width - (one.x + one.width))).toBe(28);
    });
  });

  test('пользователь листает карточки → следующая карточка встаёт к левому краю', async ({ page }) => {
    await test.step('Дано открытое слово в двух темах', async () => {
      await seedWord(page, { collections: THEMES, examples: EXAMPLES });
      await openWord(page);
    });

    await test.step('Когда он прокручивает ряд на одну карточку', async () => {
      await page.locator('.word-examples').evaluate((strip) => strip.scrollBy({ left: 300, behavior: 'instant' }));
    });

    await test.step('Тогда карточка «Alltag» стоит у левого края ряда', async () => {
      await expect.poll(async () => {
        const strip = await page.locator('.word-examples').boundingBox();
        const box = await card(page, 'Alltag').boundingBox();
        return Math.round(box.x - strip.x);
      }).toBe(28);
    });
  });
});
