const { test, expect } = require('@playwright/test');

// GH-412: the arrows moved the active index in state and Enter picked by it,
// but no row on screen was ever marked — the view compared a decorated item
// against the undecorated active value, and the two are never equal. The user
// pressed the arrows blind. These assertions are on the mark itself, which is
// the only thing the eye has to go on.
//
// `fe` matches four fixture lemmas (README.md), so there is room to move down
// twice and back up once.

const options = (page) => page.getByRole('option');

// The value field's label follows the mode, so the German field is addressed
// by the label it carries while the value is a single word.
const valueField = (page) => page.getByLabel('Слово (немецкий)');

const expectOnlyActive = async (page, index, total) => {
  for (let i = 0; i < total; i += 1) {
    if (i === index) {
      await expect(options(page).nth(i)).toHaveAttribute('data-active', '');
    } else {
      await expect(options(page).nth(i)).not.toHaveAttribute('data-active', '');
    }
  }
};

test('the arrows move the visible highlight through the suggestions', async ({ page }) => {
  await page.goto('/home');

  const field = valueField(page);
  await expect(field).toBeVisible();

  // The dictionary lives in a Worker that fetches and imports SQLite; a third
  // suggestion appearing is the only honest signal that it is ready and that
  // the fixture is the dictionary being served.
  await field.pressSequentially('fe');
  await expect(options(page)).toHaveCount(4);

  // A fresh list is already on its first entry — that is what Enter picks
  // without any arrow press.
  await expectOnlyActive(page, 0, 4);

  await page.keyboard.press('ArrowDown');
  await expectOnlyActive(page, 1, 4);

  await page.keyboard.press('ArrowDown');
  await expectOnlyActive(page, 2, 4);

  await page.keyboard.press('ArrowUp');
  await expectOnlyActive(page, 1, 4);
});

test('the highlight stops at both ends of the list', async ({ page }) => {
  await page.goto('/home');

  const field = valueField(page);
  await field.pressSequentially('fe');
  await expect(options(page)).toHaveCount(4);

  await page.keyboard.press('ArrowUp');
  await expectOnlyActive(page, 0, 4);

  for (let i = 0; i < 6; i += 1) {
    await page.keyboard.press('ArrowDown');
  }
  await expectOnlyActive(page, 3, 4);
});

test('the marked entry is the one Enter picks', async ({ page }) => {
  await page.goto('/home');

  const field = valueField(page);
  await field.pressSequentially('fe');
  await expect(options(page)).toHaveCount(4);

  await page.keyboard.press('ArrowDown');
  const marked = await options(page).nth(1).innerText();

  await page.keyboard.press('Enter');
  await expect(field).toHaveValue(marked.trim());
});

test('the scroll effect can find the marked entry', async ({ page }) => {
  await page.goto('/home');

  const field = valueField(page);
  await field.pressSequentially('fe');
  await expect(options(page)).toHaveCount(4);

  // A CSS locator on purpose, and the only one in this suite besides
  // dictionary-focus.spec.js: this string is not a locator of convenience but
  // the literal selector `:action/handler-word-keydown` hands to
  // `:effect/scroll-nearest`. Asserting it resolves to exactly one element is
  // asserting the scroll has something to scroll to — before the fix
  // `querySelector` returned null and a long list never followed the arrows.
  await page.keyboard.press('ArrowDown');
  await expect(page.locator('.suggestions [data-active]')).toHaveCount(1);
});
