const { test, expect, docsOfType } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// A lesson passed lights a star in the shell bar, counted for today; the end
// of a lesson offers one more (#715).

const ANSWERS = { дом: 'Haus', собака: 'Hund' };

const homeHeading = (page) => page.getByRole('heading', { name: 'Главная' });
const progress = (page) => page.getByRole('progressbar', { name: 'Прогресс урока' });
const stars = (page) => page.getByRole('img', { name: /^Уроков за сегодня/ });
const oneMore = (page) => page.getByRole('button', { name: 'ЕЩЁ УРОК' });

async function startLesson(page) {
  await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
  await expect(page).toHaveURL(/\/lesson$/);
  await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
}

// Answers every trial of the lesson on screen, two words of two trials.
async function passLesson(page) {
  for (let trial = 0; trial < 2; trial += 1) {
    await expect(page.locator('#lesson-answer')).toBeVisible();
    const prompt = (await page.locator('#lesson-challenge .lesson__prompt').textContent()).trim();
    await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(ANSWERS[prompt]);
    await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
    await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    if (trial === 0) await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  }
}

// Lessons passed, written straight into the user database (README, "Seeding
// from a spec"): `ages` are how many days before now each was passed.
async function seedLessons(page, ages) {
  await page.evaluate(async (ages) => {
    const docs = ages.map((age, index) => ({
      _id: 'lesson-seed-' + index,
      type: 'lesson',
      passed_at: new Date(Date.now() - age * 86400000).toISOString(),
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, ages);
}

test.describe('Звёзды за уроки', () => {
  test('ученик проходит урок → в шапке звезда, «ЕЩЁ УРОК» сразу начинает новый урок', async ({ page }) => {
    await test.step('Дано два слова и начатый урок', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await addWord(page, 'Hund', 'собака');
      await expect(stars(page)).toHaveCount(0);
      await startLesson(page);
    });

    await test.step('Когда он верно отвечает на последнее задание', async () => {
      await passLesson(page);
    });

    await test.step('Тогда в шапке одна звезда, а «ЕЩЁ УРОК» в фокусе над «ЗАКОНЧИТЬ»', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 1');
      await expect(oneMore(page)).toBeFocused();
      await expect(page.getByRole('button', { name: 'ЗАКОНЧИТЬ' })).toBeVisible();
      expect(await docsOfType(page, 'user-db', 'lesson')).toHaveLength(1);
    });

    await test.step('Когда он жмёт «ЕЩЁ УРОК»', async () => {
      await oneMore(page).click();
    });

    await test.step('Тогда на экране новый урок с нулевым прогрессом, прошлого урока в ленте нет, звезда на месте', async () => {
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
      await expect(page.locator('#lesson-answer')).toBeVisible();
      await expect(page.locator('.lesson__prompt')).toHaveCount(1);
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 1');
    });

    await test.step('Когда он проходит и его, и жмёт «ЗАКОНЧИТЬ»', async () => {
      await passLesson(page);
      await page.getByRole('button', { name: 'ЗАКОНЧИТЬ' }).click();
    });

    await test.step('Тогда он на главной, и в шапке две звезды', async () => {
      await expect(homeHeading(page)).toBeVisible();
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 2');
      await expect(stars(page).locator('svg')).toHaveCount(2);
    });
  });

  test('ученик верно отвечает на последнее задание → «ЕЩЁ УРОК» на месте «ДАЛЕЕ», «ЗАКОНЧИТЬ» в строке «Правильно!»', async ({ page }) => {
    let nextBox;

    await test.step('Дано два слова одной длины и верный ответ на первое задание', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await addWord(page, 'Hund', 'собака');
      await startLesson(page);
      const prompt = (await page.locator('#lesson-challenge .lesson__prompt').textContent()).trim();
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(ANSWERS[prompt]);
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
      nextBox = await page.getByRole('button', { name: 'ДАЛЕЕ' }).boundingBox();
      await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
    });

    await test.step('Когда он верно отвечает на последнее', async () => {
      await expect(page.locator('#lesson-answer')).toBeVisible();
      const prompt = (await page.locator('#lesson-challenge .lesson__prompt').textContent()).trim();
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(ANSWERS[prompt]);
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
    });

    await test.step('Тогда «ЕЩЁ УРОК» стоит там же, где стояла «ДАЛЕЕ», а «ЗАКОНЧИТЬ» — в строке «Правильно!»', async () => {
      await expect(oneMore(page)).toBeFocused();
      expect(await oneMore(page).boundingBox()).toEqual(nextBox);
      const verdict = await page.getByRole('heading', { name: 'Правильно!' }).boundingBox();
      const finish = await page.getByRole('button', { name: 'ЗАКОНЧИТЬ' }).boundingBox();
      expect(Math.abs((finish.y + finish.height / 2) - (verdict.y + verdict.height / 2))).toBeLessThan(2);
      expect(finish.x).toBeGreaterThan(verdict.x + verdict.width);
    });
  });

  test('ученик с уроками за сегодня и вчера → в шапке только сегодняшние, пять золотых — одна фиолетовая', async ({ page }) => {
    await test.step('Дано семь уроков сегодня и три вчера', async () => {
      await page.goto('/home');
      await expect(homeHeading(page)).toBeVisible();
      await seedLessons(page, [0, 0, 0, 0, 0, 0, 0, 1.1, 1.2, 1.3]);
    });

    await test.step('Когда он открывает приложение', async () => {
      await page.reload();
    });

    await test.step('Тогда в шапке одна фиолетовая и две золотые звезды', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 7');
      await expect(stars(page).locator('.app-shell__star--purple')).toHaveCount(1);
      await expect(stars(page).locator('svg')).toHaveCount(3);
    });
  });

  test('ученик с двадцатью шестью уроками сегодня → в шапке «5», фиолетовая и золотая звёзды', async ({ page }) => {
    await test.step('Дано двадцать шесть уроков сегодня', async () => {
      await page.goto('/home');
      await expect(homeHeading(page)).toBeVisible();
      await seedLessons(page, Array(26).fill(0));
      await page.reload();
    });

    await test.step('Тогда в шапке число 5, одна фиолетовая и одна золотая звезда', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 26');
      await expect(stars(page)).toContainText('5');
      await expect(stars(page).locator('.app-shell__star--purple')).toHaveCount(1);
      await expect(stars(page).locator('svg')).toHaveCount(2);
    });
  });
});
