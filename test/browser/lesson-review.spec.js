const { test, expect } = require('./fixtures');
const { placeTiles } = require('./lesson-answer.shared');
const { answerWordThenShowExample, prompt, next, tray, bank } = require('./lesson-review.shared');

// The trials answered in a lesson can be looked back at (#734).

const back = (page) => page.getByRole('button', { name: 'Предыдущее задание' });
const progress = (page) => page.getByRole('progressbar', { name: 'Прогресс урока' });

test.describe('Пройденные задания урока', () => {
  test('ученик нажимает стрелку назад → видит прошлое задание и его ответ, прогресс как до него', async ({ page }) => {
    await test.step('Дано слово отвечено с первой попытки, на экране пример', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '50');
    });

    await test.step('Когда он нажимает стрелку назад', async () => {
      await back(page).click();
    });

    await test.step('Тогда видно задание слова, «Правильно!», его ответ, и прогресс на нуле', async () => {
      await expect(prompt(page, 'пёс')).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
      await expect(page.locator('.lesson__footer--past')).toContainText('der Hund');
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
      await expect(back(page)).toBeDisabled();
    });
  });

  test('ученик ошибся в слове, потом ответил и вернулся к нему → «Попыток: 2»', async ({ page }) => {
    await test.step('Дано слово отвечено со второй попытки, на экране пример', async () => {
      await answerWordThenShowExample(page, { missFirst: true });
    });

    await test.step('Когда он возвращается к слову', async () => {
      await back(page).click();
    });

    await test.step('Тогда у слова одна страница, «Попыток: 2»', async () => {
      await expect(page.getByRole('heading', { name: 'Попыток: 2' })).toBeVisible();
      await expect(back(page)).toBeDisabled();
    });
  });

  test('ученик собрал часть ответа, вернулся назад и нажал «ДАЛЕЕ» → снова пример, собранное на месте', async ({ page }) => {
    await test.step('Дано на экране пример, в лотке две плитки', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
      await placeTiles(page, 'Der Hund');
      await expect(tray(page).getByRole('button')).toHaveText(['Der', 'Hund']);
    });

    await test.step('Когда он возвращается к слову и нажимает «ДАЛЕЕ»', async () => {
      await back(page).click();
      await expect(prompt(page, 'пёс')).toBeVisible();
      await next(page).click();
    });

    await test.step('Тогда снова пример, плитки в лотке те же, прогресс прежний', async () => {
      await expect(prompt(page, 'Пёс спит в саду.')).toBeVisible();
      await expect(tray(page).getByRole('button')).toHaveText(['Der', 'Hund']);
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '50');
    });
  });

  test('ученик ответил на последнее задание и вернулся назад → у прошлого задания «ЗАКОНЧИТЬ», урок закончен', async ({ page }) => {
    await test.step('Дано пример верно собран, урок окончен', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
      await placeTiles(page, 'Der Hund schläft im Garten.');
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
      await expect(page.getByRole('button', { name: 'ЗАКОНЧИТЬ' })).toBeVisible();
      await expect(bank(page)).toHaveCount(0);
    });

    await test.step('Когда он возвращается к слову', async () => {
      await back(page).click();
    });

    await test.step('Тогда видно слово и его ответ, кнопка «ЗАКОНЧИТЬ»', async () => {
      await expect(prompt(page, 'пёс')).toBeVisible();
      await expect(page.locator('.lesson__footer--past')).toContainText('der Hund');
      await expect(next(page)).toHaveCount(0);
    });

    await test.step('Когда он нажимает «ЗАКОНЧИТЬ»', async () => {
      await page.getByRole('button', { name: 'ЗАКОНЧИТЬ' }).click();
    });

    await test.step('Тогда урок закончен, он на главной', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });
});
