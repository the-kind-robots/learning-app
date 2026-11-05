const { test, expect } = require('./fixtures');
const { placeTiles } = require('./lesson-answer.shared');
const { answerWordThenShowExample, prompt, next, tray, bank } = require('./lesson-review.shared');

// The trials answered in a lesson can be looked back at (#734).

const back = (page) => page.getByRole('button', { name: 'Предыдущее задание' });
const progress = (page) => page.getByRole('progressbar', { name: 'Прогресс урока' });
const oneMore = (page) => page.getByRole('button', { name: 'ЕЩЁ УРОК' });

async function finishLesson(page) {
  await answerWordThenShowExample(page, { missFirst: false });
  await placeTiles(page, 'Der Hund schläft im Garten.');
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await oneMore(page).waitFor();
}

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

  test('ученик возвращается к заданию с панелью другой высоты → панель меняет высоту плавно, не скачком', async ({ page }) => {
    await test.step('Дано слово отвечено, на экране пример с плитками', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
    });

    const frames = await test.step('Когда он нажимает стрелку назад', async () => {
      // On every frame from just before the press until long after: the height of the colour the panel paints behind
      // its panels and the bottom of the button showing.
      await page.evaluate(() => {
        const panel = document.querySelector('.lesson__panel');
        const start = performance.now();
        window.panelFrames = { done: false, values: [] };
        const sample = () => {
          const button = [...panel.querySelectorAll('.big-button')].find((b) => b.checkVisibility({ visibilityProperty: true }));
          window.panelFrames.values.push({
            height: parseFloat(getComputedStyle(panel, '::before').height),
            bottom: button.getBoundingClientRect().bottom,
          });
          if (performance.now() - start < 1200) requestAnimationFrame(sample);
          else window.panelFrames.done = true;
        };
        requestAnimationFrame(sample);
      });
      await back(page).click();
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
      await page.waitForFunction(() => window.panelFrames.done);
      return page.evaluate(() => window.panelFrames.values);
    });

    await test.step('Тогда панель проходит высоты между прежней и новой и встаёт на новую, кнопка не двигается', async () => {
      const heights = frames.map((f) => f.height);
      const from = heights[0];
      const to = heights[heights.length - 1];
      const low = Math.min(from, to);
      const high = Math.max(from, to);
      expect(high - low).toBeGreaterThan(20);
      // A jump shows no height in between; a busy machine samples few frames of the 280 ms, but never none.
      expect(heights.filter((h) => h > low + 1 && h < high - 1).length).toBeGreaterThan(0);
      const bottoms = frames.map((f) => f.bottom);
      expect(Math.max(...bottoms) - Math.min(...bottoms)).toBeLessThan(1);
    });
  });

  test('ученик после конца урока возвращается назад → «ЗАКОНЧИТЬ» едет вместе с кромкой панели и не гаснет', async ({ page }) => {
    const sentence = 'Der Hund schläft im Garten, während es draußen den ganzen Tag regnet und der Wind laut weht.';

    await test.step('Дано урок окончен на примере, ответ которого в две строки', async () => {
      await answerWordThenShowExample(page, {
        missFirst: false,
        example: { translation: 'Пёс спит в саду, пока весь день идёт дождь и громко дует ветер.', sentence },
      });
      await placeTiles(page, sentence);
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
      await oneMore(page).waitFor();
    });

    const frames = await test.step('Когда он нажимает стрелку назад', async () => {
      // On every frame from just before the press until long after: the height of the panel's colour, how far
      // «ЗАКОНЧИТЬ» stands below its top edge, and how much of «ЗАКОНЧИТЬ» shows.
      await page.evaluate(() => {
        const panel = document.querySelector('.lesson__panel');
        const finish = [...panel.querySelectorAll('button')].find((b) => b.textContent.trim() === 'ЗАКОНЧИТЬ');
        const start = performance.now();
        window.finishFrames = { done: false, values: [] };
        const sample = () => {
          const height = parseFloat(getComputedStyle(panel, '::before').height);
          const edge = panel.getBoundingClientRect().bottom - height;
          let shown = 1;
          for (let el = finish; el && el !== panel; el = el.parentElement) shown *= Number(getComputedStyle(el).opacity);
          window.finishFrames.values.push({ height, below: finish.getBoundingClientRect().top - edge, shown });
          if (performance.now() - start < 1200) requestAnimationFrame(sample);
          else window.finishFrames.done = true;
        };
        requestAnimationFrame(sample);
      });
      await back(page).click();
      await expect(prompt(page, 'пёс')).toBeVisible();
      await page.waitForFunction(() => window.finishFrames.done);
      return page.evaluate(() => window.finishFrames.values);
    });

    await test.step('Тогда кромка проходит высоты между, а «ЗАКОНЧИТЬ» всё время на одном расстоянии от неё и видна целиком', async () => {
      const heights = frames.map((f) => f.height);
      const low = Math.min(heights[0], heights[heights.length - 1]);
      const high = Math.max(heights[0], heights[heights.length - 1]);
      expect(high - low).toBeGreaterThan(10);
      // A jump shows no height in between; a busy machine samples few frames of the 280 ms, but never none.
      expect(heights.filter((h) => h > low + 1 && h < high - 1).length).toBeGreaterThan(0);
      const below = frames.map((f) => f.below);
      expect(Math.max(...below) - Math.min(...below)).toBeLessThan(1);
      expect(frames.every((f) => f.shown === 1)).toBe(true);
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

  test('ученик ответил на последнее задание и вернулся назад → у прошлого задания «ЕЩЁ УРОК» и «ЗАКОНЧИТЬ», «ЗАКОНЧИТЬ» ведёт на главную', async ({ page }) => {
    await test.step('Дано пример верно собран, урок окончен', async () => {
      await finishLesson(page);
    });

    await test.step('Когда он возвращается к слову', async () => {
      await back(page).click();
    });

    await test.step('Тогда видно слово и его ответ, кнопки «ЕЩЁ УРОК» и «ЗАКОНЧИТЬ»', async () => {
      await expect(prompt(page, 'пёс')).toBeVisible();
      await expect(page.locator('.lesson__footer--past')).toContainText('der Hund');
      // The current panel's own pair fades away with the rest of it.
      await expect(oneMore(page)).toHaveCount(1);
      await expect(page.getByRole('button', { name: 'ЗАКОНЧИТЬ' })).toHaveCount(1);
      await expect(oneMore(page)).toBeVisible();
      await expect(page.getByRole('button', { name: 'ЗАКОНЧИТЬ' })).toBeVisible();
      await expect(next(page)).toHaveCount(0);
    });

    await test.step('Когда он нажимает «ЗАКОНЧИТЬ»', async () => {
      await page.getByRole('button', { name: 'ЗАКОНЧИТЬ' }).click();
    });

    await test.step('Тогда урок закончен, он на главной', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('ученик после конца урока вернулся к слову и жмёт «ЕЩЁ УРОК» → новый урок с первого задания', async ({ page }) => {
    await test.step('Дано урок окончен, на экране прошлое задание', async () => {
      await finishLesson(page);
      await back(page).click();
      await expect(prompt(page, 'пёс')).toBeVisible();
    });

    await test.step('Когда он жмёт «ЕЩЁ УРОК»', async () => {
      await oneMore(page).click();
    });

    await test.step('Тогда на экране первое задание нового урока, прогресс на нуле', async () => {
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
      await expect(page.getByRole('textbox', { name: 'Ответ на немецком' })).toBeVisible();
      await expect(back(page)).toBeHidden();
    });
  });
});
