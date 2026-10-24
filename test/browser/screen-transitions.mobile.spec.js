const { test, expect, nothingHappensFor } = require('./fixtures');
const { seedWords } = require('./seed.shared');
const { recordTransitions, transitions, forgetTransitions } = require('./screen-transitions.shared');

// How a screen change moves on a phone (#634, ADR-0024). What the browser
// drew is read from the transition's animations: which snapshot moved, and
// along which keyframes. Not proven here: the back gesture the browser
// animates itself, which a headless browser cannot make.

const DEEPER = [{
  kind: 'deeper',
  animations: ['::view-transition-new(root) screen-enter-from-right', '::view-transition-old(root) screen-recede-left'],
}];

const BACK = [{
  kind: 'back',
  animations: ['::view-transition-new(root) screen-return-from-left', '::view-transition-old(root) screen-leave-right'],
}];

async function seedCollections(page, names) {
  await page.evaluate(async (names) => {
    const now = new Date().toISOString();
    const docs = names.map((name, i) => ({
      _id: 'collection:' + i, type: 'collection', name, word_ids: [], created_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, names);
}

// Home over two words and two collections, with the transitions recorded.
async function openHome(page) {
  await recordTransitions(page);
  await page.goto('/home');
  await seedWords(page, ['der Hund', 'die Katze']);
  await seedCollections(page, ['Arbeit', 'Reise']);
  await page.goto('/home');
  await expect(page.getByRole('button', { name: 'Список слов' })).toBeVisible();
  await forgetTransitions(page);
}

test.describe('Переходы между экранами на телефоне', () => {
  test('ученик открывает список слов → экран въезжает справа, главная уходит влево', async ({ page }) => {
    await test.step('Дано главная', async () => {
      await openHome(page);
    });

    await test.step('Когда он нажимает «Список слов»', async () => {
      await page.getByRole('button', { name: 'Список слов' }).click();
    });

    await test.step('Тогда экран слов въезжает справа', async () => {
      await expect(page.getByPlaceholder('Поиск')).toBeVisible();
      await expect.poll(() => transitions(page)).toEqual(DEEPER);
    });
  });

  test('ученик открывает наборы → экран въезжает справа, главная уходит влево', async ({ page }) => {
    await test.step('Дано главная', async () => {
      await openHome(page);
    });

    await test.step('Когда он нажимает «Открыть наборы»', async () => {
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Тогда экран наборов въезжает справа', async () => {
      await expect(page).toHaveURL(/\/collections$/);
      await expect.poll(() => transitions(page)).toEqual(DEEPER);
    });
  });

  const WAYS_BACK = [
    ['закрывает список слов крестиком', (page) => page.getByRole('button', { name: 'Закрыть' }).click()],
    ['возвращается из списка слов кнопкой «Назад» браузера', (page) => page.goBack()],
  ];

  for (const [how, goBack] of WAYS_BACK) {
    test(`ученик ${how} → экран уезжает вправо, главная возвращается слева`, async ({ page }) => {
      await test.step('Дано открытый список слов', async () => {
        await openHome(page);
        await page.getByRole('button', { name: 'Список слов' }).click();
        await expect(page.getByPlaceholder('Поиск')).toBeVisible();
        await expect.poll(() => transitions(page)).toEqual(DEEPER);
        await forgetTransitions(page);
      });

      await test.step(`Когда он ${how}`, async () => {
        await goBack(page);
      });

      await test.step('Тогда список уезжает вправо, главная возвращается', async () => {
        await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
        await expect.poll(() => transitions(page)).toEqual(BACK);
      });
    });
  }

  test('ученик выбирает набор → наборы уезжают вправо, на главной уже его название', async ({ page }) => {
    await test.step('Дано открытые наборы', async () => {
      await openHome(page);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await expect.poll(() => transitions(page)).toEqual(DEEPER);
      await forgetTransitions(page);
    });

    await test.step('Когда он нажимает набор «Reise»', async () => {
      await page.getByRole('button', { name: /^Reise/ }).click();
    });

    await test.step('Тогда наборы уезжают вправо, а заголовок главной — «Reise»', async () => {
      await expect(page.getByRole('heading', { name: 'Reise', exact: true })).toBeVisible();
      await expect.poll(() => transitions(page)).toEqual(BACK);
    });
  });

  test('ученик начинает и закрывает урок → экраны растворяются друг в друге, вбок ничего не едет', async ({ page }) => {
    await test.step('Дано главная', async () => {
      await openHome(page);
    });

    await test.step('Когда он нажимает «НАЧАТЬ УРОК»', async () => {
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
    });

    await test.step('Тогда главная гаснет, урок проступает чуть крупнее', async () => {
      await expect(page.getByRole('progressbar', { name: 'Прогресс урока' })).toBeVisible();
      await expect.poll(() => transitions(page)).toEqual([{
        kind: 'fade-in',
        animations: ['::view-transition-new(root) screen-fade-in-from-larger', '::view-transition-old(root) screen-fade-out-smaller'],
      }]);
      await forgetTransitions(page);
    });

    await test.step('Когда он закрывает урок крестиком', async () => {
      await page.getByRole('button', { name: 'Закрыть' }).click();
    });

    await test.step('Тогда урок гаснет, разрастаясь, и проступает главная', async () => {
      await expect(page.getByRole('button', { name: 'НАЧАТЬ УРОК' })).toBeVisible();
      await expect.poll(() => transitions(page)).toEqual([{
        kind: 'fade-out',
        animations: ['::view-transition-new(root) screen-fade-in-from-smaller', '::view-transition-old(root) screen-fade-out-larger'],
      }]);
    });
  });

  test('ученик нажимает «ДАЛЕЕ» → задание уходит влево, следующее приходит справа', async ({ page }) => {
    await test.step('Дано урок и проверенный ответ', async () => {
      await openHome(page);
      await page.goto('/lesson');
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('falsch');
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
      await expect(page.getByRole('button', { name: 'ДАЛЕЕ' })).toBeVisible();
      await forgetTransitions(page);
    });

    await test.step('Когда он нажимает «ДАЛЕЕ»', async () => {
      await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
    });

    await test.step('Тогда сдвигается только задание', async () => {
      await expect(page.getByRole('button', { name: 'ПРОВЕРИТЬ' })).toBeVisible();
      await expect.poll(() => transitions(page)).toEqual([{
        kind: 'next-trial',
        animations: ['::view-transition-new(lesson-trial) trial-enter-from-right', '::view-transition-old(lesson-trial) trial-leave-left'],
      }]);
    });
  });

  test('ученик удаляет набор → его плитка сжимается, остальные встают на её место', async ({ page }) => {
    await test.step('Дано открытые наборы', async () => {
      await openHome(page);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await expect(page.getByRole('button', { name: /^Arbeit/ })).toBeVisible();
      await expect.poll(() => transitions(page)).toEqual(DEEPER);
      await forgetTransitions(page);
    });

    await test.step('Когда он удаляет «Arbeit»', async () => {
      const close = page.getByRole('button', { name: 'Удалить набор «Arbeit»' });
      await close.focus();
      await close.press('Enter');
    });

    await test.step('Тогда сжимается одна плитка — удалённая', async () => {
      await expect(page.getByRole('button', { name: /^Arbeit/ })).toHaveCount(0);
      await expect.poll(async () => (await transitions(page))
        .map(({ kind, animations }) => [kind, animations.filter((a) => a.endsWith(' tile-shrink')).length]))
        .toEqual([['close-gap', 1]]);
    });
  });

  test('при «уменьшить движение» ученик открывает список слов → экран сменяется сразу', async ({ page }) => {
    await test.step('Дано главная и просьба системы уменьшить движение', async () => {
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await openHome(page);
    });

    await test.step('Когда он нажимает «Список слов»', async () => {
      await page.getByRole('button', { name: 'Список слов' }).click();
    });

    await test.step('Тогда перехода нет', async () => {
      await expect(page.getByPlaceholder('Поиск')).toBeVisible();
      await nothingHappensFor(page, 500);
      expect(await transitions(page)).toEqual([]);
    });
  });
});
