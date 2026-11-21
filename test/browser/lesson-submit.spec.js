const { test, expect, docsOfType, nothingHappensFor } = require('./fixtures');
const { addWord } = require('./add-form.shared');

const answerField = (page) => page.getByRole('textbox', { name: 'Ответ на немецком' });

const reviews = (page) => docsOfType(page, 'user-db', 'review');

// The page hidden and shown again, as a switch to another tab does it.
const hideAndShow = (page) =>
  page.evaluate(() => {
    for (const state of ['hidden', 'visible']) {
      Object.defineProperty(document, 'visibilityState', { value: state, configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    }
  });

test.describe('Отправка ответа с клавиатуры', () => {
  test('ученик вводит ответ и жмёт Ctrl+Enter → ответ проверен', async ({ page }) => {
    await test.step('Дано урок из одного слова и введённый верный ответ', async () => {
      await page.goto('/home');
      await addWord(page, 'der Hund', 'пёс');
      await page.goto('/lesson');
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('der Hund');
    });

    await test.step('Когда он жмёт Ctrl+Enter', async () => {
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).press('Control+Enter');
    });

    await test.step('Тогда он видит «Правильно!»', async () => {
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    });
  });

  test('ученик вводит ответ и жмёт Enter → ответ не проверен, строка не перенесена', async ({ page }) => {
    await test.step('Дано урок из одного слова и введённый верный ответ', async () => {
      await page.goto('/home');
      await addWord(page, 'der Hund', 'пёс');
      await page.goto('/lesson');
      await answerField(page).fill('der Hund');
    });

    await test.step('Когда он жмёт Enter', async () => {
      await answerField(page).press('Enter');
      await nothingHappensFor(page, 500);
    });

    await test.step('Тогда «ПРОВЕРИТЬ» на месте, в поле набранное, без переноса строки', async () => {
      await expect(page.getByRole('button', { name: 'ПРОВЕРИТЬ' })).toBeVisible();
      await expect(answerField(page)).toHaveValue('der Hund');
    });
  });
});

test.describe('Конец урока с клавиатуры', () => {
  test('ученик после последнего ответа урока жмёт Enter дважды → новый урок, ответ не проверен', async ({ page }) => {
    const answers = { 'дом': 'Haus', 'собака': 'Hund' };

    await test.step('Дано урок из двух слов отвечен, «ЕЩЁ УРОК» в фокусе', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await addWord(page, 'Hund', 'собака');
      await page.goto('/lesson');
      for (let trial = 0; trial < 2; trial += 1) {
        const prompt = (await page.getByRole('heading', { level: 2 }).textContent()).trim();
        await answerField(page).fill(answers[prompt]);
        await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
        if (trial === 0) await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
      }
      await expect(page.getByRole('button', { name: 'ЕЩЁ УРОК' })).toBeFocused();
    });

    await test.step('Когда он жмёт Enter дважды', async () => {
      await page.keyboard.press('Enter');
      await expect(answerField(page)).toBeFocused();
      await page.keyboard.press('Enter');
      await nothingHappensFor(page, 500);
    });

    await test.step('Тогда на экране первое задание нового урока, ответ не проверен', async () => {
      await expect(page.getByRole('button', { name: 'ПРОВЕРИТЬ' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Правильно:', exact: true })).toBeHidden();
      await expect(answerField(page)).toHaveValue('');
      expect(await reviews(page)).toHaveLength(2);
    });
  });
});

test.describe('Время ответа', () => {
  test('ученик отвечает на слово дважды → каждый отзыв хранит время от своего показа задания', async ({ page }) => {
    await test.step('Дано урок из одного слова', async () => {
      await page.clock.install();
      await page.goto('/home');
      await addWord(page, 'der Hund', 'пёс');
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
      await expect(answerField(page)).toBeVisible();
    });

    await test.step('Когда он думает полторы секунды и отправляет пустой ответ', async () => {
      await page.clock.fastForward(1500);
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
      await expect(page.getByRole('heading', { name: 'Правильно:' })).toBeVisible();
    });

    await test.step('И по «ДАЛЕЕ» сразу отвечает верно', async () => {
      await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
      await answerField(page).fill('der Hund');
      await answerField(page).press('Control+Enter');
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    });

    await test.step('Тогда неверный ответ хранит не меньше полутора секунд, а верный — меньше', async () => {
      await expect.poll(async () => (await reviews(page)).length).toBe(2);
      const [wrong, right] = (await reviews(page))
        .sort((a, b) => a['created-at'].localeCompare(b['created-at']));
      expect(wrong.retained).toBe(false);
      expect(wrong['answer-ms']).toBeGreaterThanOrEqual(1500);
      expect(right.retained).toBe(true);
      expect(right['answer-ms']).toBeLessThan(1500);
    });
  });

  test('ученик уходит на другую вкладку во время задания → отзыв без времени ответа', async ({ page }) => {
    await test.step('Дано урок из одного слова', async () => {
      await page.goto('/home');
      await addWord(page, 'der Hund', 'пёс');
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
      await expect(answerField(page)).toBeVisible();
    });

    await test.step('Когда он уходит на другую вкладку, возвращается и отвечает', async () => {
      await hideAndShow(page);
      await answerField(page).fill('der Hund');
      await answerField(page).press('Control+Enter');
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    });

    await test.step('Тогда его отзыв записан без времени ответа', async () => {
      await expect.poll(async () => (await reviews(page)).length).toBe(1);
      expect((await reviews(page)).map((review) => review['answer-ms'])).toEqual([undefined]);
    });
  });
});
