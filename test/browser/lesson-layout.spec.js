const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// On desktop the lesson is one column: the progress bar, the field, the panel's answer and the main button share
// its edges, and the main button keeps its place in every state (#751).

const ANSWERS = { дом: 'Haus', собака: 'Hund' };

const column = (page) => page.getByRole('progressbar', { name: 'Прогресс урока' }).boundingBox();

// Each box spans the column: the same left edge and width, to a pixel.
function expectSpans(box, edges) {
  expect(Math.abs(box.x - edges.x)).toBeLessThan(1);
  expect(Math.abs(box.width - edges.width)).toBeLessThan(1);
}

async function answer(page, text) {
  await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(text);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
}

test('ученик на компьютере ошибается и доходит до конца урока → «ПРОВЕРИТЬ», «ДАЛЕЕ» и «ЕЩЁ УРОК» во всю колонку полосы прогресса и поля, ответ от её края', async ({ page }) => {
  let edges;

  await test.step('Дано два слова и начатый урок', async () => {
    await page.goto('/home');
    await addWord(page, 'Haus', 'дом');
    await addWord(page, 'Hund', 'собака');
    await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
    await expect(page.locator('#lesson-answer')).toBeVisible();
    edges = await column(page);
  });

  await test.step('Тогда поле и «ПРОВЕРИТЬ» во всю колонку', async () => {
    expectSpans(await page.locator('#lesson-answer').boundingBox(), edges);
    expectSpans(await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).boundingBox(), edges);
  });

  await test.step('Когда он отвечает неверно', async () => {
    await answer(page, 'Falsch');
  });

  await test.step('Тогда «ДАЛЕЕ» во всю колонку, ответ начинается от её края', async () => {
    expectSpans(await page.getByRole('button', { name: 'ДАЛЕЕ' }).boundingBox(), edges);
    const label = await page.getByRole('heading', { name: 'Правильно:' }).boundingBox();
    expect(Math.abs(label.x - edges.x)).toBeLessThan(1);
  });

  await test.step('Когда он верно отвечает до конца урока', async () => {
    await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
    const again = page.getByRole('button', { name: 'ЕЩЁ УРОК' });
    while (!(await again.isVisible())) {
      await expect(page.locator('#lesson-answer')).toBeVisible();
      const prompt = (await page.locator('#lesson-challenge .lesson__prompt').textContent()).trim();
      await answer(page, ANSWERS[prompt]);
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
      if (await page.getByRole('button', { name: 'ДАЛЕЕ' }).isVisible()) {
        await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
      }
    }
  });

  await test.step('Тогда «ЕЩЁ УРОК» во всю колонку', async () => {
    expectSpans(await page.getByRole('button', { name: 'ЕЩЁ УРОК' }).boundingBox(), edges);
  });
});
