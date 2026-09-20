const { test, expect } = require('@playwright/test');

// One word is all a lesson needs to exist, and adding it is also what makes
// the lesson footer — and with it the hotkey — live.
const addWord = async (page) => {
  await page.getByLabel('Слово (немецкий)').fill('Haus');
  await page.getByLabel('Перевод (русский)').fill('дом');
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
  await expect(page.getByRole('button', { name: 'НАЧАТЬ УРОК' })).toBeVisible();
};

test('Alt+Enter from the word field starts the lesson', async ({ page }) => {
  await page.goto('/home');
  await addWord(page);

  await page.getByLabel('Слово (немецкий)').focus();
  await page.keyboard.press('Alt+Enter');

  await expect(page).toHaveURL(/\/lesson$/);
  await expect(page.getByLabel('Ответ на немецком')).toBeVisible();
});

test('Alt+Enter from the translation field starts the lesson', async ({ page }) => {
  await page.goto('/home');
  await addWord(page);

  const translation = page.getByLabel('Перевод (русский)');
  await translation.focus();
  await translation.fill('черновик');
  await page.keyboard.press('Alt+Enter');

  await expect(page).toHaveURL(/\/lesson$/);

  // The keystroke started a lesson and nothing else: the draft translation
  // sitting in the field was not submitted as a word.
  await page.goto('/words');
  await expect(
    page.getByRole('listitem').filter({ hasText: 'черновик' })
  ).toHaveCount(0);
  await expect(
    page.getByRole('listitem').filter({ hasText: 'Haus' })
  ).toHaveCount(1);
});

test('Alt+Enter outside the fields starts the lesson', async ({ page }) => {
  await page.goto('/home');
  await addWord(page);

  await page.getByRole('button', { name: 'Список слов' }).focus();
  await page.keyboard.press('Alt+Enter');

  await expect(page).toHaveURL(/\/lesson$/);
});

test('Alt+Enter does nothing while the vocabulary is empty', async ({ page }) => {
  await page.goto('/home');
  await expect(page.getByRole('button', { name: 'НАЧАТЬ УРОК' })).toBeHidden();

  await page.getByLabel('Слово (немецкий)').focus();
  await page.keyboard.press('Alt+Enter');

  // Asserting that navigation never happens: auto-waiting can only wait for
  // something to become true, so the only way to establish the negative is to
  // let a navigation's worth of time pass first.
  await page.waitForTimeout(500);
  await expect(page).toHaveURL(/\/home$/);
});

test('the screen keeps its other Enter keys', async ({ page }) => {
  await page.goto('/home');

  // Enter on the word field moves on to the translation.
  const word = page.getByLabel('Слово (немецкий)');
  await word.focus();
  await word.fill('Haus');
  await page.keyboard.press('Enter');
  await expect(page.getByLabel('Перевод (русский)')).toBeFocused();

  // Ctrl+Enter on the translation field still adds the word.
  await page.keyboard.type('дом');
  await page.keyboard.press('Control+Enter');
  await expect(page.getByRole('button', { name: 'НАЧАТЬ УРОК' })).toBeVisible();

  await page.goto('/words');
  const item = page.getByRole('listitem').filter({ hasText: 'Haus' });
  await expect(item).toBeVisible();
  await expect(item).toContainText('дом');
});
