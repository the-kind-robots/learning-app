const { test, expect } = require('@playwright/test');

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

const options = (page) => page.getByRole('option');

// The value field's label follows the mode, so it is addressed by id: the
// element is the same one throughout (inputs are not remounted).
const valueField = (page) => page.locator('#new-word-value');

// The visually hidden legend carries the same words as the panel title, so
// only the role tells them apart.
const panelTitle = (page, name) => page.getByRole('heading', { name });

test('typing on from a picked suggestion switches the form to phrase mode', async ({ page }) => {
  await page.goto('/home');

  const field = valueField(page);
  await expect(field).toBeVisible();

  // The dictionary lives in a Worker that fetches and imports SQLite; a
  // suggestion appearing is the only honest signal that it is ready.
  await field.pressSequentially('Haus');
  const picked = options(page).filter({ hasText: 'das Haus' }).first();
  await expect(picked).toBeVisible();
  await picked.click();

  await expect(field).toHaveValue('das Haus');
  await expect(panelTitle(page, 'Добавить слово')).toBeVisible();
  await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('das Haus');

  // The click hands focus to the translation field, so typing on means going
  // back to the end of the value.
  await field.click();
  await page.keyboard.press('End');
  await page.keyboard.type(' ist gross');

  await expect(panelTitle(page, 'Добавить фразу')).toBeVisible();
  await expect(page.getByLabel('Фраза (немецкий)')).toHaveValue('das Haus ist gross');
});
