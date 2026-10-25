const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { seedWords } = require('./seed.shared');
const { recordTransitions, transitions, forgetTransitions, screenSettled, motionSettled, animationsOf } = require('./screen-transitions.shared');

// Motion inside a screen on a phone (#634, ADR-0024): CSS animations on the
// element that appears, read from the element itself; a new tile is a view
// transition, read as the screen changes are.

async function openLesson(page) {
  await page.goto('/home');
  await seedWords(page, ['der Hund']);
  await page.goto('/lesson');
  await expect(page.getByRole('textbox', { name: 'Ответ на немецком' })).toBeVisible();
}

async function checkAnswer(page, answer) {
  await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(answer);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
}

const answerPanel = (page) => page.locator('#lesson-footer > .lesson__footer');

async function openWords(page) {
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page.getByPlaceholder('Поиск')).toBeVisible();
  await screenSettled(page);
}

test.describe('Движение внутри экрана на телефоне', () => {
  test('ученик проверяет верный ответ → панель «Правильно!» поднимается снизу', async ({ page }) => {
    await test.step('Дано урок', async () => {
      await openLesson(page);
    });

    await test.step('Когда он проверяет верный ответ', async () => {
      await checkAnswer(page, 'der Hund');
    });

    await test.step('Тогда панель поднимается снизу', async () => {
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
      expect(await animationsOf(answerPanel(page))).toEqual(['rise-from-bottom']);
    });
  });

  test('ученик проверяет неверный ответ → панель поднимается, строка с его ответом вздрагивает', async ({ page }) => {
    await test.step('Дано урок', async () => {
      await openLesson(page);
    });

    await test.step('Когда он проверяет неверный ответ', async () => {
      await checkAnswer(page, 'falsch');
    });

    await test.step('Тогда панель поднимается, а его ответ вздрагивает', async () => {
      await expect(page.getByRole('heading', { name: 'Ваш ответ:' })).toBeVisible();
      expect(await animationsOf(answerPanel(page))).toEqual(['rise-from-bottom']);
      expect(await animationsOf(page.getByText('falsch', { exact: true }))).toEqual(['shake']);
    });
  });

  test('ученик проверяет ответ → экран урока стоит на месте, пока поднимается панель', async ({ page }) => {
    await test.step('Дано урок, где каждый кадр записывает, где полоса прогресса', async () => {
      await openLesson(page);
      await page.evaluate(() => {
        window.__progressTops = [];
        const until = performance.now() + 2000;
        const record = () => {
          window.__progressTops.push(Math.round(document.querySelector('[role=progressbar]').getBoundingClientRect().top));
          if (performance.now() < until) requestAnimationFrame(record);
        };
        requestAnimationFrame(record);
      });
    });

    await test.step('Когда он проверяет неверный ответ', async () => {
      await checkAnswer(page, 'falsch');
      await expect(page.getByRole('heading', { name: 'Ваш ответ:' })).toBeVisible();
      await motionSettled(page);
    });

    await test.step('Тогда полоса прогресса ни на одном кадре не сдвинулась', async () => {
      const tops = await page.evaluate(() => window.__progressTops);
      expect(new Set(tops).size).toBe(1);
    });
  });

  test('ученик открывает слово в списке → окно поднимается снизу, фон за ним проступает', async ({ page }) => {
    await test.step('Дано список слов', async () => {
      await page.goto('/home');
      await seedWords(page, ['der Hund']);
      await page.goto('/words');
      await expect(page.getByRole('button', { name: /der Hund/ })).toBeVisible();
    });

    await test.step('Когда он открывает «der Hund»', async () => {
      await page.getByRole('button', { name: /der Hund/ }).click();
    });

    await test.step('Тогда окно поднимается снизу, а фон проступает', async () => {
      const dialog = page.getByRole('dialog');
      await expect(dialog).toBeVisible();
      expect(await animationsOf(dialog)).toEqual(['::backdrop fade-in', 'rise-from-bottom']);
    });
  });

  test('ученик добавляет слово и открывает список → подсвечена строка только нового слова', async ({ page }) => {
    await test.step('Дано главная со словом «die Katze»', async () => {
      await page.goto('/home');
      await seedWords(page, ['die Katze']);
      await page.goto('/home');
    });

    await test.step('Когда он добавляет «Haus» и открывает список', async () => {
      await addWord(page, 'Haus', 'дом');
      await openWords(page);
    });

    await test.step('Тогда подсвечена строка «Haus», а «die Katze» — нет', async () => {
      expect(await animationsOf(page.getByRole('listitem').filter({ hasText: 'Haus' }))).toEqual(['row-fresh']);
      expect(await animationsOf(page.getByRole('listitem').filter({ hasText: 'die Katze' }))).toEqual([]);
    });
  });

  test('ученик открывает список ещё раз, ничего не добавив → подсветки нет', async ({ page }) => {
    await test.step('Дано список, открытый после добавления «Haus»', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await openWords(page);
      expect(await animationsOf(page.getByRole('listitem').filter({ hasText: 'Haus' }))).toEqual(['row-fresh']);
    });

    await test.step('Когда он закрывает список и открывает снова', async () => {
      await page.getByRole('button', { name: 'Закрыть' }).click();
      await screenSettled(page);
      await openWords(page);
    });

    await test.step('Тогда ни одна строка не подсвечена', async () => {
      expect(await animationsOf(page.getByRole('listitem').filter({ hasText: 'Haus' }))).toEqual([]);
    });
  });

  test('ученик создаёт набор → его плитка вырастает на своём месте, соседние расступаются', async ({ page }) => {
    await test.step('Дано наборы', async () => {
      await recordTransitions(page);
      await page.goto('/collections');
      await expect(page.getByRole('button', { name: 'Новый набор' })).toBeVisible();
      await screenSettled(page);
      await forgetTransitions(page);
    });

    await test.step('Когда он создаёт набор «Arbeit»', async () => {
      page.once('dialog', (dialog) => dialog.accept('Arbeit'));
      await page.getByRole('button', { name: 'Новый набор' }).click();
    });

    await test.step('Тогда вырастает одна плитка — новая', async () => {
      await expect(page.getByRole('button', { name: /^Arbeit/ })).toBeVisible();
      await expect.poll(async () => (await transitions(page))
        .map(({ kind, animations }) => [kind, animations.filter((a) => a.endsWith(' tile-grow')).length]))
        .toEqual([['open-gap', 1]]);
    });
  });

  test('при «уменьшить движение» ученик открывает слово → окно на месте сразу', async ({ page }) => {
    await test.step('Дано список слов и просьба системы уменьшить движение', async () => {
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.goto('/home');
      await seedWords(page, ['der Hund']);
      await page.goto('/words');
    });

    await test.step('Когда он открывает «der Hund»', async () => {
      await page.getByRole('button', { name: /der Hund/ }).click();
    });

    await test.step('Тогда окно на месте без движения', async () => {
      await expect(page.getByRole('dialog')).toBeVisible();
      expect(await animationsOf(page.getByRole('dialog'))).toEqual([]);
    });
  });
});
