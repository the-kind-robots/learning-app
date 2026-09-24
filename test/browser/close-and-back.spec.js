const { test, expect } = require('@playwright/test');

// Every screen but home closes from the corner, and home has no app screen
// behind it (#411, ADR-0015).

const close = (page) => page.getByRole('button', { name: 'Закрыть' });
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
  await expect(page.getByRole('button', { name: 'Открыть наборы' })).toBeVisible();
  await expect(close(page)).toHaveCount(0);

  await addWord(page);

  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page.getByPlaceholder('Поиск')).toBeVisible();
  await expect(close(page)).toHaveCount(1);
  await close(page).click();
  await expect(homeHeading(page)).toBeVisible();

  await page.getByRole('button', { name: 'Открыть наборы' }).click();
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

// Leaving a lesson ends it, whatever did the leaving: Back, the corner close,
// the finish button (#484). The stored lesson is the evidence — nothing on
// home renders it, and entering again would start fresh either way. Read at
// the engine level, as test/browser/README.md prescribes.
async function storedLessons(page) {
  return page.evaluate(async () => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    const found = await db.find(db.use('device-db'), toClj({ selector: { type: 'lesson' } }));
    return cljs.core.count(cljs.core.get(found, kw('docs')));
  });
}

const ANSWERS = { дом: 'Haus', собака: 'Hund' };

async function addWords(page) {
  for (const [translation, word] of Object.entries(ANSWERS)) {
    await page.getByLabel('Слово (немецкий)').fill(word);
    await page.getByLabel('Перевод (русский)').fill(translation);
    await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
    await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('');
  }
}

const progress = (page) => page.getByRole('progressbar', { name: 'Прогресс урока' });

async function startLesson(page) {
  await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
  await expect(page).toHaveURL(/\/lesson$/);
  await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
  await expect.poll(() => storedLessons(page)).toBe(1);
}

// The answer field is on screen only while a trial waits for its answer, so
// the prompt read after it is the current trial's, not the one just passed.
async function answerCorrectly(page) {
  await expect(page.locator('#lesson-answer')).toBeVisible();
  const prompt = (await page.locator('.lesson__prompt').textContent()).trim();
  await page.locator('#lesson-answer').fill(ANSWERS[prompt]);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
}

test('Back out of a lesson ends it; entering again starts fresh', async ({ page }) => {
  await page.goto('/home');
  await addWords(page);
  await startLesson(page);
  await answerCorrectly(page);
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(progress(page)).not.toHaveAttribute('aria-valuenow', '0');

  await page.goBack();
  await expect(page).toHaveURL(/\/home$/);
  await expect(homeHeading(page)).toBeVisible();
  await expect.poll(() => storedLessons(page)).toBe(0);

  await startLesson(page);
});

test('closing a lesson from the corner ends it', async ({ page }) => {
  await page.goto('/home');
  await addWords(page);
  await startLesson(page);
  await answerCorrectly(page);

  await close(page).click();
  await expect(homeHeading(page)).toBeVisible();
  await expect.poll(() => storedLessons(page)).toBe(0);
});

test('finishing a lesson ends it and goes home', async ({ page }) => {
  await page.goto('/home');
  await addWords(page);
  await startLesson(page);
  await answerCorrectly(page);
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await answerCorrectly(page);

  await page.getByRole('button', { name: 'ЗАКОНЧИТЬ' }).click();
  await expect(page).toHaveURL(/\/home$/);
  await expect(homeHeading(page)).toBeVisible();
  await expect.poll(() => storedLessons(page)).toBe(0);
});
