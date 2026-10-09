const { test, expect, nothingHappensFor } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// One word is all a lesson needs to exist, and adding it is also what makes
// the lesson footer — and with it the hotkey — live.
const addHaus = async (page) => {
  await addWord(page, 'Haus', 'дом');
  await expect(page.getByRole('button', { name: 'НАЧАТЬ УРОК' })).toBeVisible();
};

test.describe('Горячие клавиши главной', () => {
  test('пользователь нажимает Alt+Enter → начинается урок из любого места главной', async ({ page }) => {
    const word = page.getByLabel('Слово (немецкий)');
    const translation = page.getByLabel('Перевод (русский)');

    await test.step('Дано главная с одним словом в словаре', async () => {
      await page.goto('/home');
      await addHaus(page);
    });

    await test.step('Когда он жмёт Alt+Enter в поле слова', async () => {
      await word.focus();
      await page.keyboard.press('Alt+Enter');
    });

    await test.step('Тогда открыт урок с полем ответа', async () => {
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(page.getByLabel('Ответ на немецком')).toBeVisible();
    });

    await test.step('Когда он возвращается и жмёт Alt+Enter в поле перевода с черновиком', async () => {
      await page.goto('/home');
      await translation.focus();
      await translation.fill('черновик');
      await page.keyboard.press('Alt+Enter');
    });

    await test.step('Тогда урок открыт, а черновик не сохранён как слово', async () => {
      await expect(page).toHaveURL(/\/lesson$/);
      await page.goto('/words');
      await expect(page.getByRole('listitem').filter({ hasText: 'черновик' })).toHaveCount(0);
      await expect(page.getByRole('listitem').filter({ hasText: 'Haus' })).toHaveCount(1);
    });

    await test.step('Когда он жмёт Alt+Enter вне полей, на кнопке «Список слов»', async () => {
      await page.goto('/home');
      await page.getByRole('button', { name: 'Список слов' }).focus();
      await page.keyboard.press('Alt+Enter');
    });

    await test.step('Тогда снова открыт урок', async () => {
      await expect(page).toHaveURL(/\/lesson$/);
    });
  });

  test('пользователь нажимает Alt+Enter при пустом словаре → ничего не происходит', async ({ page }) => {
    await test.step('Дано главная без слов, кнопки урока нет', async () => {
      await page.goto('/home');
      await expect(page.getByRole('button', { name: 'НАЧАТЬ УРОК' })).toBeHidden();
    });

    await test.step('Когда он жмёт Alt+Enter в поле слова', async () => {
      await page.getByLabel('Слово (немецкий)').focus();
      await page.keyboard.press('Alt+Enter');
      await nothingHappensFor(page, 500);
    });

    await test.step('Тогда он остаётся на главной', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('пользователь жмёт Enter и Ctrl+Enter в форме → Enter переходит к переводу, Ctrl+Enter добавляет слово', async ({ page }) => {
    const word = page.getByLabel('Слово (немецкий)');

    await test.step('Дано главная, поле слова заполнено', async () => {
      await page.goto('/home');
      await word.focus();
      await word.fill('Haus');
    });

    await test.step('Когда он жмёт Enter', async () => {
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда фокус в поле перевода', async () => {
      await expect(page.getByLabel('Перевод (русский)')).toBeFocused();
    });

    await test.step('Когда он вводит перевод и жмёт Ctrl+Enter', async () => {
      await page.keyboard.type('дом');
      await page.keyboard.press('Control+Enter');
    });

    await test.step('Тогда слово добавлено и видно в списке', async () => {
      await expect(page.getByRole('button', { name: 'НАЧАТЬ УРОК' })).toBeVisible();
      await page.goto('/words');
      const item = page.getByRole('listitem').filter({ hasText: 'Haus' });
      await expect(item).toBeVisible();
      await expect(item).toContainText('дом');
    });
  });
});
