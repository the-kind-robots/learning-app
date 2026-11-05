const { test, expect, nothingHappensFor } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { placeTiles } = require('./lesson-answer.shared');
const { answerWordThenShowExample, field, check, prompt, next, tray, bank, wheelBack } = require('./lesson-review.shared');

// The trials answered in a lesson can be looked back at (#734), from the keyboard too (#771).

const progress = (page) => page.getByRole('progressbar', { name: 'Прогресс урока' });
const oneMore = (page) => page.getByRole('button', { name: 'ЕЩЁ УРОК' });
// Every trial on the strip, the ones hidden too: a prompt is its trial's heading.
const prompts = (page) => page.getByRole('heading', { level: 2, includeHidden: true });

async function finishLesson(page) {
  await answerWordThenShowExample(page, { missFirst: false });
  await placeTiles(page, 'Der Hund schläft im Garten.');
  await check(page).click();
  await oneMore(page).waitFor();
}

// Three words, each answered right: the lesson is over with two trials behind the last one.
async function finishThreeWords(page) {
  const answers = { 'пёс': 'der Hund', 'кошка': 'die Katze', 'дом': 'das Haus' };
  await page.goto('/home');
  for (const [translation, word] of Object.entries(answers)) await addWord(page, word, translation);
  await page.goto('/lesson');
  for (let trial = 1; trial <= 3; trial++) {
    const shown = await page.getByRole('heading', { level: 2 }).textContent();
    await field(page).fill(answers[shown]);
    await check(page).click();
    if (trial < 3) await next(page).click();
  }
  await oneMore(page).waitFor();
}

test.describe('Пройденные задания урока', () => {
  test('ученик листает колесом назад → видит прошлое задание и его ответ, прогресс как до него, стрелки у прогресса нет', async ({ page }) => {
    await test.step('Дано слово отвечено с первой попытки, на экране пример', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '50');
    });

    await test.step('Когда он листает колесом назад', async () => {
      await wheelBack(page);
    });

    await test.step('Тогда видно задание слова, «Правильно!», его ответ, и прогресс на нуле', async () => {
      await expect(prompt(page, 'пёс')).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
      await expect(page.locator('.lesson__footer--past')).toContainText('der Hund');
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
      await expect(page.getByRole('banner').getByRole('button')).toHaveCount(0);
    });
  });

  test('ученик после конца урока листает ← и → → прошлое задание, дальше первого не уходит, → возвращает к последнему', async ({ page }) => {
    await test.step('Дано урок окончен', async () => {
      await finishLesson(page);
    });

    await test.step('Когда он жмёт ←', async () => {
      await page.keyboard.press('ArrowLeft');
    });

    await test.step('Тогда видно задание слова', async () => {
      await expect(prompt(page, 'пёс')).toBeVisible();
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
    });

    await test.step('Когда он жмёт ← ещё раз', async () => {
      await page.keyboard.press('ArrowLeft');
      await nothingHappensFor(page, 500);
    });

    await test.step('Тогда по-прежнему задание слова', async () => {
      await expect(prompt(page, 'пёс')).toBeVisible();
    });

    await test.step('Когда он жмёт →', async () => {
      await page.keyboard.press('ArrowRight');
    });

    await test.step('Тогда снова пример и его «ЕЩЁ УРОК»', async () => {
      await expect(prompt(page, 'Пёс спит в саду.')).toBeVisible();
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '100');
      await expect(oneMore(page)).toBeVisible();
    });
  });

  test('ученик набирает ответ и жмёт ← → задание то же, набранное на месте', async ({ page }) => {
    const answers = { 'пёс': 'der Hund', 'кошка': 'die Katze' };
    let second;
    await test.step('Дано одно слово отвечено, во втором набрано начало ответа', async () => {
      await page.goto('/home');
      await addWord(page, 'der Hund', 'пёс');
      await addWord(page, 'die Katze', 'кошка');
      await page.goto('/lesson');
      const first = await page.getByRole('heading', { level: 2 }).textContent();
      await field(page).fill(answers[first]);
      await check(page).click();
      await next(page).click();
      second = first === 'пёс' ? 'кошка' : 'пёс';
      await expect(prompt(page, second)).toBeVisible();
      await field(page).fill('die');
    });

    await test.step('Когда он жмёт ←', async () => {
      await page.keyboard.press('ArrowLeft');
      await nothingHappensFor(page, 500);
    });

    await test.step('Тогда на экране то же слово, набранное в поле', async () => {
      await expect(prompt(page, second)).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeHidden();
      await expect(field(page)).toHaveValue('die');
    });
  });

  test('ученик на прошлом задании жмёт Enter или Ctrl+Enter → снова текущее, Enter там жмёт «ДАЛЕЕ»', async ({ page }) => {
    await test.step('Дано пример собран неверно, на экране «ДАЛЕЕ»', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
      await placeTiles(page, 'Hund Der schläft im Garten');
      await check(page).click();
      await expect(next(page)).toBeVisible();
    });

    await test.step('Когда он жмёт ←, затем Enter', async () => {
      await page.keyboard.press('ArrowLeft');
      await expect(prompt(page, 'пёс')).toBeVisible();
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда снова пример с его ответом', async () => {
      await expect(prompt(page, 'Пёс спит в саду.')).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeHidden();
    });

    await test.step('Когда он жмёт ←, затем Ctrl+Enter', async () => {
      await page.keyboard.press('ArrowLeft');
      await expect(prompt(page, 'пёс')).toBeVisible();
      await page.keyboard.press('Control+Enter');
    });

    await test.step('Тогда снова пример с его ответом', async () => {
      await expect(prompt(page, 'Пёс спит в саду.')).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeHidden();
    });

    await test.step('Когда он жмёт Enter', async () => {
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда пример задан снова, лоток пуст', async () => {
      await expect(check(page)).toBeVisible();
      await expect(tray(page).getByRole('button')).toHaveCount(0);
    });
  });

  test('ученик колесом уходит назад от неотвеченного примера и жмёт Enter дважды → снова пример, ответ не проверен', async ({ page }) => {
    await test.step('Дано на экране пример, ещё без ответа', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
      await expect(check(page)).toBeVisible();
    });

    await test.step('Когда он листает колесом назад и жмёт Enter', async () => {
      await wheelBack(page);
      await expect(prompt(page, 'пёс')).toBeVisible();
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда снова пример', async () => {
      await expect(prompt(page, 'Пёс спит в саду.')).toBeVisible();
      await expect(check(page)).toBeVisible();
    });

    await test.step('Когда он жмёт Enter ещё раз', async () => {
      await page.keyboard.press('Enter');
      await nothingHappensFor(page, 500);
    });

    await test.step('Тогда ответ не проверен: «ПРОВЕРИТЬ» на месте, ошибки нет', async () => {
      await expect(check(page)).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Правильно:', exact: true })).toBeHidden();
    });
  });

  test('ученик после конца урока листает на два задания назад и жмёт Enter → новый урок', async ({ page }) => {
    await test.step('Дано урок из трёх слов окончен', async () => {
      await finishThreeWords(page);
      await expect(prompts(page)).toHaveCount(3);
    });

    await test.step('Когда он жмёт ← дважды, затем Enter', async () => {
      await page.keyboard.press('ArrowLeft');
      await expect(progress(page)).toHaveAttribute('aria-valuenow', /^33/);
      await page.keyboard.press('ArrowLeft');
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда на экране первое задание нового урока', async () => {
      await expect(prompts(page)).toHaveCount(1);
      await expect(field(page)).toBeVisible();
    });
  });

  test('ученик после конца урока на прошлом задании жмёт Ctrl+Enter → новый урок', async ({ page }) => {
    await test.step('Дано урок окончен, на экране прошлое задание', async () => {
      await finishLesson(page);
      await page.keyboard.press('ArrowLeft');
      await expect(prompt(page, 'пёс')).toBeVisible();
    });

    await test.step('Когда он жмёт Ctrl+Enter', async () => {
      await page.keyboard.press('Control+Enter');
    });

    await test.step('Тогда на экране первое задание нового урока', async () => {
      await expect(prompts(page)).toHaveCount(1);
      await expect(field(page)).toBeVisible();
    });
  });

  test('ученик после последнего задания открыл карточку слова и жмёт Esc → закрывается только карточка', async ({ page }) => {
    await test.step('Дано урок окончен, открыта карточка слова «Garten»', async () => {
      await finishLesson(page);
      await page.getByRole('button', { name: 'Garten.', exact: true }).click();
      await expect(page.getByText('сад', { exact: true })).toBeVisible();
    });

    await test.step('Когда он жмёт Esc', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда карточки нет, урок на месте', async () => {
      await expect(page.getByText('сад', { exact: true })).toBeHidden();
      await nothingHappensFor(page, 500);
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(oneMore(page)).toBeVisible();
    });
  });

  test('ученик после последнего задания открыл окно «Сохранить мои слова» и жмёт Esc → закрывается только окно', async ({ page }) => {
    const dialog = (page) => page.getByRole('dialog', { name: 'Чтобы слова не потерялись' });

    await test.step('Дано урок окончен, открыто окно «Сохранить мои слова»', async () => {
      await finishLesson(page);
      await page.getByRole('button', { name: 'Синхронизация' }).click();
      await page.getByRole('button', { name: 'Сохранить мои слова' }).click();
      await expect(dialog(page)).toBeVisible();
    });

    await test.step('Когда он жмёт Esc', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда окна нет, урок на месте', async () => {
      await expect(dialog(page)).toBeHidden();
      await nothingHappensFor(page, 500);
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(oneMore(page)).toBeVisible();
    });
  });

  test('ученик после последнего задания жмёт Esc → урок закончен, он на главной', async ({ page }) => {
    await test.step('Дано урок окончен', async () => {
      await finishLesson(page);
    });

    await test.step('Когда он жмёт Esc', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда он на главной', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('ученик посреди урока жмёт Esc → урок на месте', async ({ page }) => {
    await test.step('Дано на экране пример после отвеченного слова', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
    });

    await test.step('Когда он жмёт Esc', async () => {
      await page.keyboard.press('Escape');
      await nothingHappensFor(page, 500);
    });

    await test.step('Тогда урок на месте', async () => {
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(prompt(page, 'Пёс спит в саду.')).toBeVisible();
    });
  });

  test('ученик возвращается к заданию с панелью другой высоты → панель меняет высоту плавно, не скачком', async ({ page }) => {
    await test.step('Дано слово отвечено, на экране пример с плитками', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
    });

    const frames = await test.step('Когда он листает колесом назад', async () => {
      // On every frame from just before the turn until long after: the height of the colour the panel paints behind
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
      await wheelBack(page);
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
      await check(page).click();
      await oneMore(page).waitFor();
    });

    const frames = await test.step('Когда он жмёт ←', async () => {
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
      await page.keyboard.press('ArrowLeft');
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
      await wheelBack(page);
    });

    await test.step('Тогда у слова одна страница, «Попыток: 2»', async () => {
      await expect(page.getByRole('heading', { name: 'Попыток: 2' })).toBeVisible();
      await expect(prompts(page)).toHaveCount(2);
    });
  });

  test('ученик собрал часть ответа, вернулся назад и нажал «ДАЛЕЕ» → снова пример, собранное на месте', async ({ page }) => {
    await test.step('Дано на экране пример, в лотке две плитки', async () => {
      await answerWordThenShowExample(page, { missFirst: false });
      await placeTiles(page, 'Der Hund');
      await expect(tray(page).getByRole('button')).toHaveText(['Der', 'Hund']);
    });

    await test.step('Когда он возвращается к слову и нажимает «ДАЛЕЕ»', async () => {
      await wheelBack(page);
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
      await page.keyboard.press('ArrowLeft');
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
      await page.keyboard.press('ArrowLeft');
      await expect(prompt(page, 'пёс')).toBeVisible();
    });

    await test.step('Когда он жмёт «ЕЩЁ УРОК»', async () => {
      await oneMore(page).click();
    });

    await test.step('Тогда на экране первое задание нового урока, прогресс на нуле', async () => {
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
      await expect(field(page)).toBeVisible();
      await expect(prompts(page)).toHaveCount(1);
    });
  });
});
