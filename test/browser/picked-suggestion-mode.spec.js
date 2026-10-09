const { test, expect } = require('./fixtures');
const { openHome } = require('./service-worker.shared');

// GH-358: picking a suggestion used to freeze the mode for the rest of the
// edit, so a phrase typed onto a picked word was saved as a word. The pick
// still decides for the lemma it was made for — that is what tells `das heißt`
// from an article pair — and stops deciding as soon as the value is something
// else.
//
// The dictionary is the fixture the backend serves from
// LEARNING_APP__DICTIONARY_DIR (see README.md); `das Haus` is one of its
// lemmas, and it is an article pair, so nothing but the pick keeps it a word
// once its own suggestion list is gone.

test.describe('Режим формы добавления', () => {
  test('пользователь выбрал подсказку и дописал текст → форма переходит в режим фразы', async ({ page }) => {
    // The value field's label follows the mode, so it is addressed by id: the
    // element is the same one throughout (inputs are not remounted).
    const field = page.locator('#new-word-value');
    // The visually hidden legend carries the same words as the panel title,
    // so only the role tells them apart.
    const title = (name) => page.getByRole('heading', { name });

    await test.step('Дано главная, в поле слова набрано «Haus»', async () => {
      await openHome(page);
      await expect(field).toBeVisible();
      await field.pressSequentially('Haus');
    });

    await test.step('Когда он выбирает подсказку «das Haus»', async () => {
      const picked = page.getByRole('option').filter({ hasText: 'das Haus' }).first();
      await expect(picked).toBeVisible();
      await picked.click();
    });

    await test.step('Тогда форма остаётся в режиме слова со значением «das Haus»', async () => {
      await expect(field).toHaveValue('das Haus');
      await expect(title('Добавить слово')).toBeVisible();
      await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('das Haus');
    });

    await test.step('Когда он дописывает « ist gross»', async () => {
      // The click hands focus to the translation field, so typing on means
      // going back to the end of the value.
      await field.click();
      await page.keyboard.press('End');
      await page.keyboard.type(' ist gross');
    });

    await test.step('Тогда форма в режиме фразы', async () => {
      await expect(title('Добавить фразу')).toBeVisible();
      await expect(page.getByLabel('Фраза (немецкий)')).toHaveValue('das Haus ist gross');
    });
  });
});
