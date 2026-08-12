const { test, expect } = require('@playwright/test');

// The add form auto-grows its two textareas and swaps copy the moment the
// input reads as a phrase. Both mechanics used to move the form under the
// cursor, and the measured height was short by the border box, which showed
// as a scrollbar inside a one-line field. These are geometry assertions —
// what the DOM says is not what the eye sees.
//
// Chrome-only, and that bounds what they prove: here `field-sizing: content`
// owns the height, so the JS measuring path these specs would otherwise cover
// runs on Safari alone. The phone layer — in-flow suggestions, the keyboard —
// needs a mobile project with a seeded dictionary (#289), not this file.

const value = () => 'Слово (немецкий)';

test('a one-line translation does not overflow its field', async ({ page }) => {
  await page.goto('/home');

  const translation = page.getByLabel('Перевод (русский)');
  await translation.fill('например, к примеру');

  const overflows = await translation.evaluate(
    (node) => node.scrollHeight > node.clientHeight
  );
  expect(overflows).toBe(false);
});

test('the value field stays put when the input becomes a phrase', async ({ page }) => {
  await page.goto('/home');

  await page.getByLabel(value()).fill('Haus');
  const before = await page.getByLabel(value()).boundingBox();

  // The space flips detection to phrase mode, which mounts the chip and
  // rewrites every label on the form — including this field's own, so the
  // locator has to follow the copy while the element stays the same one.
  await page.getByLabel(value()).fill('auf jeden Fall');
  await expect(page.getByRole('button', { name: 'фраза' })).toBeVisible();
  const after = await page.getByLabel('Фраза (немецкий)').boundingBox();

  expect(after.y).toBe(before.y);
  expect(after.x).toBe(before.x);
});

test('a submitted form leaves both fields at their empty height', async ({ page }) => {
  await page.goto('/home');

  const field = page.getByLabel(value());
  const translation = page.getByLabel('Перевод (русский)');
  const emptyHeight = (await translation.boundingBox()).height;

  await field.fill('Entschuldigung, dass ich zu spät komme');
  await translation.fill(
    'извините, что опаздываю; простите за опоздание, я застрял в пробке ' +
      'и не успел предупредить заранее'
  );
  const grown = await translation.boundingBox();
  expect(grown.height).toBeGreaterThan(emptyHeight);

  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
  await expect(page.getByRole('button', { name: 'Список слов' })).toBeVisible();

  // A height measured for the old content is an inline style; nothing else
  // clears it, so an emptied field would keep it.
  await expect(translation).toHaveValue('');
  expect((await translation.boundingBox()).height).toBe(emptyHeight);
});
