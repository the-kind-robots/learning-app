const { test, expect } = require('@playwright/test');

// Every screen but home closes from the corner, and home has no app screen
// behind it (#411, ADR-0015).

const close = (page) => page.getByRole('link', { name: 'Закрыть' });
const homeHeading = (page) => page.getByRole('heading', { name: 'Главная' });

async function addWord(page) {
  await page.getByLabel('Слово (немецкий)').fill('Haus');
  await page.getByLabel('Перевод (русский)').fill('дом');
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
  await expect(page.getByRole('button', { name: 'Список слов' })).toBeVisible();
}

// Waits for the rows, not only the address: a words read still in flight
// when the test leaves would land after home's and put the words screen back
// on display at /home — a race in the page loads, not in the history.
async function openWords(page) {
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page).toHaveURL(/\/words$/);
  await expect(page.getByRole('listitem').filter({ hasText: 'Haus' })).toBeVisible();
}

// A page of the same origin before the app, so Back from home has somewhere
// outside the app to land. Chrome replaces the initial about:blank entry, so
// that one would not do.
async function openAppAfterAnotherPage(page) {
  await page.goto('/favicon.ico');
  await page.goto('/home');
  await expect(homeHeading(page)).toBeVisible();
}

test('home has no close mark; words, themes and lesson each have one', async ({ page }) => {
  await page.goto('/home');
  await expect(homeHeading(page)).toBeVisible();
  await expect(page.getByRole('link', { name: 'Открыть наборы' })).toBeVisible();
  await expect(close(page)).toHaveCount(0);

  await addWord(page);

  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page.getByPlaceholder('Поиск')).toBeVisible();
  await expect(close(page)).toHaveCount(1);
  await close(page).click();
  await expect(homeHeading(page)).toBeVisible();

  await page.getByRole('link', { name: 'Открыть наборы' }).click();
  await expect(page).toHaveURL(/\/collections$/);
  await expect(close(page)).toHaveCount(1);
  await close(page).click();
  await expect(homeHeading(page)).toBeVisible();

  await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
  await expect(page.getByRole('progressbar', { name: 'Прогресс урока' })).toBeVisible();
  await expect(close(page)).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'Закрыть урок' })).toHaveCount(0);
  await close(page).click();
  await expect(homeHeading(page)).toBeVisible();
  await expect(page).toHaveURL(/\/home$/);
});

test('the words screen has no back button and no visible heading', async ({ page }) => {
  await page.goto('/home');
  await addWord(page);
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page.getByRole('listitem').filter({ hasText: 'Haus' })).toBeVisible();

  await expect(page.getByRole('button', { name: /Назад/ })).toHaveCount(0);
  // Kept for assistive technology, clipped to nothing on screen.
  const heading = page.getByRole('heading', { level: 1, name: 'Мои слова' });
  await expect(heading).toHaveCount(1);
  const box = await heading.boundingBox();
  expect(box.width).toBeLessThanOrEqual(1);
  expect(box.height).toBeLessThanOrEqual(1);
});

test('closing a screen leaves nothing of the app behind home', async ({ page }) => {
  await openAppAfterAnotherPage(page);
  await addWord(page);

  await openWords(page);
  await close(page).click();
  await expect(page).toHaveURL(/\/home$/);
  await expect(homeHeading(page)).toBeVisible();

  await page.goBack();
  await expect(page).toHaveURL(/\/favicon\.ico$/);
});

test('Back from a screen is home, and Back from home leaves the app', async ({ page }) => {
  await openAppAfterAnotherPage(page);
  await addWord(page);

  await openWords(page);
  await page.goBack();
  await expect(page).toHaveURL(/\/home$/);
  await expect(homeHeading(page)).toBeVisible();

  await page.goBack();
  await expect(page).toHaveURL(/\/favicon\.ico$/);
});

test('a screen opened from a screen takes its place', async ({ page }) => {
  await openAppAfterAnotherPage(page);
  await addWord(page);

  await openWords(page);
  await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
  await expect(page).toHaveURL(/\/lesson$/);
  await expect(page.getByRole('progressbar', { name: 'Прогресс урока' })).toBeVisible();
  await page.goBack();
  await expect(page).toHaveURL(/\/home$/);
  await expect(homeHeading(page)).toBeVisible();
});

test('a screen opened directly has home beneath it, reload included', async ({ page }) => {
  await page.goto('/favicon.ico');
  await page.goto('/words');
  await expect(page).toHaveURL(/\/words$/);
  await expect(page.getByText('Слов пока нет')).toBeVisible();
  await expect(close(page)).toHaveCount(1);

  // A reload finds the entry already standing on home and adds nothing.
  await page.reload();
  await expect(page.getByText('Слов пока нет')).toBeVisible();
  await expect(close(page)).toHaveCount(1);

  await page.goBack();
  await expect(page).toHaveURL(/\/home$/);
  await expect(homeHeading(page)).toBeVisible();
  await page.goBack();
  await expect(page).toHaveURL(/\/favicon\.ico$/);
});
