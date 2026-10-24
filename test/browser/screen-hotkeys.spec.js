const { test, expect, nothingHappensFor } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// The words and themes screens from the keyboard (GH-615).

const wordField = (page) => page.getByLabel('Слово (немецкий)');
const search = (page) => page.getByPlaceholder('Поиск');

const homeWithHaus = async (page) => {
  await page.goto('/home');
  await addWord(page, 'Haus', 'дом');
  await expect(page.getByRole('button', { name: 'Список слов' })).toBeVisible();
};

test.describe('Экраны слов и тем с клавиатуры', () => {
  test('пользователь жмёт Alt+W в поле слова → открыт список слов, в поле ничего не напечатано', async ({ page }) => {
    await test.step('Дано главная с одним словом, курсор в поле слова', async () => {
      await homeWithHaus(page);
      await wordField(page).focus();
    });

    await test.step('Когда он жмёт Alt+W', async () => {
      await page.keyboard.press('Alt+KeyW');
    });

    await test.step('Тогда открыт список слов со словом', async () => {
      await expect(page).toHaveURL(/\/words$/);
      await expect(page.getByRole('listitem').filter({ hasText: 'Haus' })).toBeVisible();
    });

    await test.step('Когда он возвращается на главную', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда поле слова пустое', async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(wordField(page)).toHaveValue('');
    });
  });

  test('пользователь жмёт Alt+W при пустом словаре → остаётся на главной', async ({ page }) => {
    await test.step('Дано главная без слов', async () => {
      await page.goto('/home');
      await expect(page.getByRole('button', { name: 'Список слов' })).toBeHidden();
    });

    await test.step('Когда он жмёт Alt+W', async () => {
      await wordField(page).focus();
      await page.keyboard.press('Alt+KeyW');
      await nothingHappensFor(page, 500);
    });

    await test.step('Тогда он на главной', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('пользователь жмёт Alt+T на главной → открыты темы, Esc возвращает на главную', async ({ page }) => {
    await test.step('Дано главная', async () => {
      await page.goto('/home');
      await wordField(page).focus();
    });

    await test.step('Когда он жмёт Alt+T', async () => {
      await page.keyboard.press('Alt+KeyT');
    });

    await test.step('Тогда открыты темы', async () => {
      await expect(page).toHaveURL(/\/collections$/);
    });

    await test.step('Когда он жмёт Esc', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда снова главная', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('пользователь открывает список слов на компьютере → курсор в поиске, Esc возвращает на главную', async ({ page }) => {
    await test.step('Дано главная с одним словом', async () => {
      await homeWithHaus(page);
    });

    await test.step('Когда он открывает список слов', async () => {
      await page.getByRole('button', { name: 'Список слов' }).click();
    });

    await test.step('Тогда курсор в поиске, и набранное ищет', async () => {
      await expect(search(page)).toBeFocused();
      await page.keyboard.type('xyz');
      await expect(page.getByText('Ничего не найдено')).toBeVisible();
    });

    await test.step('Когда он жмёт Esc в поиске', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда снова главная', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('пользователь жмёт Esc в диалоге слова → закрывается только диалог', async ({ page }) => {
    await test.step('Дано открыт диалог слова', async () => {
      await homeWithHaus(page);
      await page.getByRole('button', { name: 'Список слов' }).click();
      await page.getByRole('listitem').filter({ hasText: 'Haus' }).click();
      await expect(page.getByRole('dialog')).toBeVisible();
    });

    await test.step('Когда он жмёт Esc', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда диалога нет, а список слов на месте', async () => {
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await nothingHappensFor(page, 500);
      await expect(page).toHaveURL(/\/words$/);
    });
  });
});
