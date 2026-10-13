const { test, expect, nothingHappensFor, docsOfType } = require('./fixtures');
const { seedWords } = require('./seed.shared');

// On a phone «ДАЛЕЕ» and the next trial's «ПРОВЕРИТЬ» sit under the same
// finger, so the second click of a double click lands on «ПРОВЕРИТЬ» of a
// trial the learner has not seen.

const reviewCount = async (page) => (await docsOfType(page, 'user-db', 'review')).length;

// The click counts the browser reported, in order, read on `window` after the
// app's own capture listener has seen each click.
const recordClickCounts = (page) =>
  page.evaluate(() => {
    window.__clickCounts = [];
    window.addEventListener('click', (event) => window.__clickCounts.push(event.detail), { capture: true });
  });

const clickCounts = (page) => page.evaluate(() => window.__clickCounts);

const secondClickOnNext = {
  'ученик дважды кликает «ДАЛЕЕ» → переход один, следующее задание не проверено':
    (page) => page.getByRole('button', { name: 'ДАЛЕЕ' }).dblclick(),
  'ученик дважды касается «ДАЛЕЕ» → переход один, следующее задание не проверено':
    async (page) => {
      const box = await page.getByRole('button', { name: 'ДАЛЕЕ' }).boundingBox();
      const [x, y] = [box.x + box.width / 2, box.y + box.height / 2];
      await page.touchscreen.tap(x, y);
      await page.touchscreen.tap(x, y);
    },
};

test.describe('Двойное нажатие на телефоне', () => {
  for (const [title, doubleTap] of Object.entries(secondClickOnNext)) {
    test(title, async ({ page }) => {
      let before;

      await test.step('Дано урок из трёх слов, неверный ответ, на экране «ДАЛЕЕ»', async () => {
        await page.goto('/home');
        await seedWords(page, ['der Hund', 'die Katze', 'das Haus']);
        await page.goto('/lesson');
        await page.locator('#lesson-answer').fill('falsch');
        await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
        await expect(page.getByRole('button', { name: 'ДАЛЕЕ' })).toBeFocused();
        before = await reviewCount(page);
        await recordClickCounts(page);
      });

      await test.step('Когда он дважды нажимает «ДАЛЕЕ»', async () => {
        await doubleTap(page);
      });

      await test.step('Тогда следующее задание ждёт ответа, ничего не показано и отзыв не записан', async () => {
        await expect(page.locator('#lesson-answer')).toBeVisible();
        await nothingHappensFor(page, 1000);
        await expect(page.getByRole('heading', { name: 'Правильно:' })).toHaveCount(0);
        await expect(page.getByRole('button', { name: 'ДАЛЕЕ' })).toHaveCount(0);
        expect(await reviewCount(page)).toBe(before);
        expect(await clickCounts(page)).toEqual([1, 2]);
      });
    });
  }
});
