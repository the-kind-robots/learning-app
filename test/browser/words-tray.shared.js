const { expect } = require('@playwright/test');

// The words search field sits in the bottom tray, directly above the lesson
// button and as wide as it; the rows start under the shell bar with air
// between (#411). Shared by the desktop and the phone spec.

async function openWordsWithAWord(page) {
  await page.goto('/home');
  await page.getByLabel('Слово (немецкий)').fill('Haus');
  await page.getByLabel('Перевод (русский)').fill('дом');
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page.getByRole('listitem').filter({ hasText: 'Haus' })).toBeVisible();
}

// The field's box is the bordered wrapper around the input, not the input.
const searchBox = (page) => page.getByPlaceholder('Поиск').locator('..');
const lessonButton = (page) => page.getByRole('button', { name: 'НАЧАТЬ УРОК' });

async function expectSearchAboveTheButton(page) {
  const field = await searchBox(page).boundingBox();
  const button = await lessonButton(page).boundingBox();
  expect(Math.abs(field.x - button.x)).toBeLessThanOrEqual(1);
  expect(Math.abs(field.x + field.width - (button.x + button.width))).toBeLessThanOrEqual(1);
  expect(field.y + field.height).toBeLessThanOrEqual(button.y);
  expect(button.y - (field.y + field.height)).toBeLessThanOrEqual(13);
}

async function expectAirUnderTheBar(page) {
  const close = await page.getByRole('button', { name: 'Закрыть' }).boundingBox();
  const row = await page.getByRole('listitem').filter({ hasText: 'Haus' }).boundingBox();
  expect(row.y - (close.y + close.height)).toBeGreaterThanOrEqual(20);
}

module.exports = { openWordsWithAWord, expectSearchAboveTheButton, expectAirUnderTheBar, searchBox, lessonButton };
