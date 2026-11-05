const { test, expect } = require('./fixtures');
const { seedWords } = require('./seed.shared');

// The lesson and the on-screen keyboard on a phone. An example is answered
// with tiles and raises no keyboard (GH-652).

// A document honours `autofocus` once, so the first lesson after a page load
// is the one that would raise the keyboard (#646).
test('ученик открыл первый урок после запуска на телефоне → поле ответа без фокуса', async ({ page }) => {
  await test.step('Дано только что запущенное приложение со словом', async () => {
    await page.goto('/home');
    await seedWords(page, ['der Hund']);
    await page.goto('/home');
  });

  await test.step('Когда он нажимает «НАЧАТЬ УРОК»', async () => {
    await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
  });

  await test.step('Тогда поле ответа видно и не в фокусе', async () => {
    const answer = page.getByRole('textbox', { name: 'Ответ на немецком' });
    await expect(answer).toBeVisible();
    await expect(answer).not.toBeFocused();
  });
});

// Headless Chrome has no virtual keyboard to overlay the lesson: a shorter viewport stands in for one, as in a
// browser where the keyboard shortens the screen (#763, #765).
test('ученик на телефоне нажал на поле ответа и открылась клавиатура → слово задания поднимается вместе с полем, того же размера, подсказка под ним видна, полоса прогресса на месте', async ({ page }) => {
  let before;
  let fieldBefore;
  let progressBefore;
  let frames;

  await test.step('Дано начатый урок со словом', async () => {
    await page.goto('/home');
    await seedWords(page, ['der Hund']);
    await page.goto('/home');
    await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
    before = await page.getByText('перевод', { exact: true }).boundingBox();
  });

  await test.step('Когда он нажимает на поле и клавиатура укорачивает экран', async () => {
    await page.getByRole('textbox', { name: 'Ответ на немецком' }).tap();
    fieldBefore = await page.locator('#lesson-answer').boundingBox();
    progressBefore = await page.getByRole('progressbar', { name: 'Прогресс урока' }).boundingBox();
    // The prompt's top and the field's, every frame for half a second from the moment the screen shortens.
    const seen = page.evaluate(() => new Promise((resolve) => {
      const prompt = document.querySelector('#lesson-challenge .lesson__prompt');
      const field = document.querySelector('#lesson-answer');
      const tops = [];
      const start = performance.now();
      const frame = () => {
        tops.push({ field: field.getBoundingClientRect().top, prompt: prompt.getBoundingClientRect().top });
        if (performance.now() - start < 500) requestAnimationFrame(frame);
        else resolve(tops);
      };
      requestAnimationFrame(frame);
    }));
    await page.setViewportSize({ width: 390, height: 430 });
    frames = await seen;
  });

  await test.step('Тогда слово задания на новом месте в том же кадре, что и поле, той же высоты, подсказка видна под ним, полоса прогресса там же', async () => {
    const progress = await page.getByRole('progressbar', { name: 'Прогресс урока' }).boundingBox();
    expect(progress.y).toBe(progressBefore.y);
    await expect(page.getByRole('textbox', { name: 'Ответ на немецком' })).toBeFocused();
    const after = await page.getByText('перевод', { exact: true }).boundingBox();
    expect(after.y).toBeLessThan(before.y - 20);
    const fieldMoved = frames.find((frame) => frame.field < fieldBefore.y - 2);
    expect(fieldMoved.prompt).toBeCloseTo(after.y, 0);
    expect(after.height).toBeCloseTo(before.height, 0);
    const instruction = page.getByText('Переведите слово на немецкий');
    await expect(instruction).toBeInViewport();
    expect((await instruction.boundingBox()).y).toBeGreaterThanOrEqual(after.y + after.height);
  });
});
