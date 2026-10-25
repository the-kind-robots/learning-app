const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { seedExample } = require('./lesson-answer.shared');
const { seedWords } = require('./seed.shared');

// A long example on a phone with the on-screen keyboard open (GH-573). The page
// sets `interactive-widget=resizes-content`, so the keyboard shows up as a
// shorter viewport; the spec shrinks the viewport the same way instead of
// raising a keyboard, which headless Chrome has none of.

const LONG_PROMPT =
  'Вчера вечером, когда мы возвращались домой после долгого рабочего дня, ' +
  'я вдруг вспомнил, что забыл ключ от квартиры в офисе, и нам пришлось ' +
  'позвонить соседям, чтобы они впустили нас внутрь.';

async function showLongExample(page) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await seedExample(page, LONG_PROMPT);
  await page.goto('/lesson');
  await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('der Hund');
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(page.locator('.lesson__instruction')).toContainText('предложение');
}

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

test.describe('Длинный пример при открытой клавиатуре', () => {
  test('ученик открыл клавиатуру на длинном примере → задание видно целиком, поле начинается с двух строк', async ({ page }) => {
    await test.step('Дано длинный пример на экране телефона без клавиатуры', async () => {
      await showLongExample(page);
      await expect(page.locator('.lesson__instruction')).toBeVisible();
    });

    await test.step('Когда экран укорачивается под клавиатуру', async () => {
      await page.setViewportSize({ width: 390, height: 480 });
    });

    await test.step('Тогда задание целиком видно над полем, инструкции нет, поле низкое', async () => {
      await expect(page.locator('.lesson__instruction')).toBeHidden();
      const prompt = await page.locator('.lesson__prompt').boundingBox();
      const body = await page.locator('.lesson__body').boundingBox();
      const field = await page.locator('#lesson-answer').boundingBox();
      // The body clips what does not fit it, so the prompt's end must be inside.
      expect(prompt.y + prompt.height).toBeLessThanOrEqual(body.y + body.height);
      expect(body.y + body.height).toBeLessThanOrEqual(field.y);
      expect(field.height).toBeLessThan(80);
    });
  });
});
