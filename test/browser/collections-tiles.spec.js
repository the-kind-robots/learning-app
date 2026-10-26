const { test, expect, docsOfType, nothingHappensFor } = require('./fixtures');
const { seedWords } = require('./seed.shared');

// Seeded at the engine level (see README, "Seeding from a spec"): a
// collection document carries its own word ids, so no vocabulary is needed
// for the counts. `word_ids` is how the app stores `:word-ids`.
async function seedCollections(page, collections) {
  await page.evaluate(async (collections) => {
    const now = new Date().toISOString();
    const docs = collections.map(([id, name, wordIds]) => ({
      _id: 'collection:' + id, type: 'collection', name, word_ids: wordIds, created_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, collections);
}

const collectionNames = async (page) =>
  (await docsOfType(page, 'user-db', 'collection')).map((d) => d.name);

const course = [
  ['kurs', 'Kurs', ['vocab:a', 'vocab:b']],
  ['k1', 'Kurs / Kapitel 1', ['vocab:b', 'vocab:c']],
  ['k2', 'Kurs / Kapitel 2', ['vocab:d']],
  ['gram', 'Grammatik / Konnektoren', ['vocab:e', 'vocab:f']],
];

// All three targets are buttons, not divs wearing `role="button"`: the role
// alone announces a control the keyboard cannot reach. Tab is the only honest
// test of that — `.focus()` does nothing on an unfocusable element and would
// pass either way — so this walks focus and keeps what it lands on.
async function focusedStop(page) {
  return page.evaluate(() => {
    const el = document.activeElement;
    if (!el || !el.closest('.masonry')) return null;
    // A target carries its collection id; a ✕ is named by its label.
    return el.getAttribute('data-collection-id') || el.getAttribute('aria-label');
  });
}

async function tabUntil(page, locator, key = 'Tab') {
  const reached = [];
  for (let step = 0; step < 40; step += 1) {
    await page.keyboard.press(key);
    const stop = await focusedStop(page);
    if (stop) reached.push(stop);
    if (await locator.evaluate((el) => el === document.activeElement)) break;
  }
  return reached;
}

test.describe('Плитки тем', () => {
  test('пользователь открывает темы с вложенными названиями → видит папки с суммой слов в заголовке', async ({ page }) => {
    await test.step('Дано темы «Kurs», «Kurs / Kapitel 1», «Kurs / Kapitel 2» и «Grammatik / Konnektoren»', async () => {
      await page.goto('/');
      await seedCollections(page, course);
    });

    await test.step('Когда он открывает экран тем', async () => {
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Тогда папка «Kurs» насчитывает 4 слова без повторов, а главы — свои', async () => {
      await expect(page.getByRole('button', { name: 'Kurs 4', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Kapitel 1 2', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Kapitel 2 1', exact: true })).toBeVisible();
    });

    await test.step('Тогда «Grammatik» без своей темы — надпись, а не плитка', async () => {
      await expect(page.getByRole('heading', { name: 'Grammatik 2', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: /Grammatik/ })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Konnektoren 2', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Всё подряд 0', exact: true })).toBeVisible();
    });

    await test.step('Тогда вложенные темы не показаны отдельными плитками', async () => {
      await expect(page.getByRole('button', { name: /Kurs \/ Kapitel/ })).toHaveCount(0);
    });
  });

  test('пользователь открывает темы → плитки раскрашены по порядку палитры, а вложенные берут цвет папки', async ({ page }) => {
    await test.step('Дано темы «Kurs», «Kurs / Kapitel 1», «Kurs / Kapitel 2», «Grammatik / Konnektoren» и «Solo»', async () => {
      await page.goto('/');
      await seedCollections(page, course.concat([['solo', 'Solo', ['vocab:a']]]));
    });

    await test.step('Когда он открывает экран тем', async () => {
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Тогда плитки по порядку берут зелёный, голубой, оранжевый, розовый цвета палитры', async () => {
      const accents = await page.locator('.masonry > .tile').evaluateAll((tiles) =>
        tiles.map((tile) => getComputedStyle(tile).getPropertyValue('--accent').replace(/\s/g, '')));
      // «Всё подряд», «Grammatik», «Kurs», «Solo»: owl, macaw, fox, starfish.
      expect(accents).toEqual(['rgb(88,204,2)', 'rgb(28,176,246)', 'rgb(255,150,0)', 'rgb(255,134,208)']);
    });

    await test.step('Тогда строки папки «Kurs» читают её цвет', async () => {
      const row = await page.getByRole('button', { name: 'Kapitel 1 2', exact: true })
        .evaluate((el) => getComputedStyle(el).getPropertyValue('--accent').replace(/\s/g, ''));
      expect(row).toBe('rgb(255,150,0)');
    });
  });

  test('пользователь ходит по темам с клавиатуры → доходит до каждой плитки, видит ✕ при фокусе и удаляет тему Enter', async ({ page }) => {
    const tile = page.getByRole('button', { name: 'Solo 1', exact: true });
    const close = page.getByRole('button', { name: 'Удалить набор «Solo»' });

    await test.step('Дано экран тем с активной темой «Solo»', async () => {
      await page.goto('/');
      await seedCollections(page, course.concat([['solo', 'Solo', ['vocab:a']]]));
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await tile.click();
      await expect(page.getByRole('heading', { name: 'Solo', exact: true })).toBeVisible();
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Тогда активна только «Solo», а её ✕ скрыт и не нажимается', async () => {
      await expect(tile).toHaveAttribute('aria-current', 'true');
      await expect(page.locator('.masonry [aria-current]')).toHaveCount(1);
      await expect(close).toHaveCSS('opacity', '0');
      await expect(close).toHaveCSS('pointer-events', 'none');
    });

    await test.step('Когда он идёт Tab-ом до «Solo»', async () => {
      // «Всё подряд», the Grammatik folder's one row, the Kurs header and its
      // two rows, then the plain tile: every target in reading order, and
      // every named collection's ✕ right after it.
      expect(await tabUntil(page, tile)).toEqual([
        'main',
        'collection:gram', 'Удалить набор «Konnektoren»',
        'collection:kurs', 'Удалить набор «Kurs»',
        'collection:k1', 'Удалить набор «Kapitel 1»',
        'collection:k2', 'Удалить набор «Kapitel 2»',
        'collection:solo',
      ]);
    });

    await test.step('Тогда ✕ плитки виден вместо счётчика', async () => {
      await expect(close).toHaveCSS('opacity', '1');
      await expect(close).toHaveCSS('pointer-events', 'auto');
      await expect(tile.locator('.tile__count')).toHaveCSS('opacity', '0');
    });

    await test.step('Когда он жмёт Tab и Enter и соглашается', async () => {
      await page.keyboard.press('Tab');
      await expect(close).toBeFocused();
      page.once('dialog', (dialog) => dialog.accept());
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда «Solo» удалена, фокус на «Вернуть» на её месте, это объявлено, «Всё подряд» активна', async () => {
      await expect(tile).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Вернуть набор «Solo»' })).toBeFocused();
      await expect(page.getByRole('status')).toHaveText('Набор «Solo» удалён');
      expect(await collectionNames(page)).not.toContain('Solo');
      await expect(page.getByRole('button', { name: 'Всё подряд 0', exact: true })).toHaveAttribute('aria-current', 'true');
    });

    await test.step('Когда он идёт Shift+Tab до ✕ папки «Kurs», жмёт Enter и соглашается', async () => {
      await tabUntil(page, page.getByRole('button', { name: 'Удалить набор «Kurs»' }), 'Shift+Tab');
      page.once('dialog', (dialog) => dialog.accept());
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда на месте заголовка «Kurs» след с «Вернуть», главы на месте', async () => {
      await expect(page.getByRole('button', { name: 'Kurs 4', exact: true })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Вернуть набор «Kurs»' })).toBeFocused();
      await expect(page.getByRole('button', { name: 'Kapitel 1 2', exact: true })).toBeVisible();
      await expect(page.getByRole('status')).toHaveText('Набор «Kurs» удалён');
    });
  });

  test('пользователь нажимает ✕ и отказывается → тема остаётся', async ({ page }) => {
    let question = null;

    await test.step('Дано экран тем', async () => {
      await page.goto('/');
      await seedCollections(page, [['solo', 'Solo', ['vocab:a']]]);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Когда он нажимает ✕ у «Solo» и отказывается', async () => {
      page.once('dialog', (dialog) => { question = dialog.message(); dialog.dismiss(); });
      await page.getByRole('button', { name: 'Удалить набор «Solo»' }).focus();
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда вопрос назвал тему, а она на месте', async () => {
      await expect.poll(() => question).toBe('Удалить набор «Solo»? Слова останутся в словаре.');
      await expect(page.getByRole('button', { name: 'Solo 1', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: /^Вернуть/ })).toHaveCount(0);
      expect(await collectionNames(page)).toContain('Solo');
    });
  });

  test('пользователь удаляет главу и жмёт «Вернуть» → глава снова на месте со своими словами', async ({ page }) => {
    const row = page.getByRole('button', { name: 'Kapitel 1 2', exact: true });
    const restore = page.getByRole('button', { name: 'Вернуть набор «Kapitel 1»' });
    let before;

    await test.step('Дано экран тем с папкой «Kurs»', async () => {
      await page.goto('/');
      await seedCollections(page, course);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      before = await page.getByRole('button', { name: 'Kapitel 2 1', exact: true }).boundingBox();
    });

    await test.step('Когда он удаляет «Kapitel 1»', async () => {
      page.once('dialog', (dialog) => dialog.accept());
      await page.getByRole('button', { name: 'Удалить набор «Kapitel 1»' }).focus();
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда на месте строки зачёркнутое имя и «Вернуть», соседи не сдвинулись', async () => {
      await expect(row).toHaveCount(0);
      await expect(restore).toBeFocused();
      await expect(page.getByText('Kapitel 1', { exact: true })).toHaveCSS('text-decoration-line', 'line-through');
      expect(await collectionNames(page)).not.toContain('Kurs / Kapitel 1');
      const after = await page.getByRole('button', { name: 'Kapitel 2 1', exact: true }).boundingBox();
      expect(Math.abs(after.y - before.y)).toBeLessThanOrEqual(1);
    });

    await test.step('Когда он жмёт «Вернуть»', async () => {
      await restore.press('Enter');
    });

    await test.step('Тогда глава на месте со своими словами, в фокусе, это объявлено', async () => {
      await expect(row).toBeFocused();
      await expect(restore).toHaveCount(0);
      await expect(page.getByRole('status')).toHaveText('Набор «Kapitel 1» возвращён');
      const docs = await docsOfType(page, 'user-db', 'collection');
      const chapter = docs.find((d) => d._id === 'collection:k1');
      expect(chapter.name).toBe('Kurs / Kapitel 1');
      // Read back through the app's own reader, which names `word_ids` as `word-ids`.
      expect(chapter['word-ids']).toEqual(['vocab:b', 'vocab:c']);
    });
  });

  test('пользователь удаляет тему и заново открывает экран тем → следа нет', async ({ page }) => {
    await test.step('Дано тема «Solo» удалена', async () => {
      await page.goto('/');
      await seedCollections(page, [['solo', 'Solo', ['vocab:a']], ['duo', 'Duo', []]]);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      page.once('dialog', (dialog) => dialog.accept());
      await page.getByRole('button', { name: 'Удалить набор «Solo»' }).focus();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('button', { name: 'Вернуть набор «Solo»' })).toBeVisible();
    });

    await test.step('Когда он открывает «Duo» и снова экран тем', async () => {
      await page.getByRole('button', { name: 'Duo 0', exact: true }).click();
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Тогда ни «Solo», ни следа', async () => {
      await expect(page.getByRole('button', { name: 'Duo 0', exact: true })).toBeVisible();
      await expect(page.getByText('Solo', { exact: true })).toHaveCount(0);
      await expect(page.getByRole('button', { name: /^Вернуть/ })).toHaveCount(0);
    });
  });

  test('пользователь нажимает на надпись папки и создаёт тему с этим именем → надпись становится плиткой', async ({ page }) => {
    await test.step('Дано папка «Grammatik» без собственной темы', async () => {
      await page.goto('/');
      await seedCollections(page, course);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Когда он нажимает на надпись «Grammatik»', async () => {
      await page.getByRole('heading', { name: 'Grammatik 2', exact: true }).click();
    });

    await test.step('Тогда он остаётся на экране тем, новая тема не создана', async () => {
      await expect(page.getByRole('heading', { name: 'Наборы' })).toBeVisible();
      expect(await collectionNames(page)).not.toContain('Grammatik');
    });

    await test.step('Когда он создаёт набор «Grammatik» кнопкой «+»', async () => {
      page.once('dialog', (dialog) => dialog.accept('Grammatik'));
      await page.getByRole('button', { name: 'Новый набор' }).click();
    });

    await test.step('Тогда «Grammatik» — плитка с суммой слов детей', async () => {
      await expect(page.getByRole('button', { name: 'Grammatik 2', exact: true })).toBeVisible();
      expect(await collectionNames(page)).toContain('Grammatik');
    });
  });

  test('пользователь переименовывает тему в уже занятое имя → отказ, плитка на каждое имя одна', async ({ page }) => {
    await test.step('Дано открыта тема «Kurs»', async () => {
      await page.goto('/');
      await seedCollections(page, course);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await page.getByRole('button', { name: 'Kurs 4', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Kurs' })).toBeVisible();
    });

    await test.step('Когда он вписывает в заголовок «kurs / kapitel 1» и жмёт Enter', async () => {
      // The heading is the inline rename (contenteditable plaintext-only,
      // which fill() does not recognise — typed instead).
      await page.getByRole('heading', { name: 'Kurs' }).click();
      await page.keyboard.press('ControlOrMeta+a');
      await page.keyboard.type(' kurs / kapitel 1 ');
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда заголовок вернулся к «Kurs», имена не задвоились', async () => {
      await expect(page.getByRole('heading', { name: 'Kurs', exact: true })).toBeVisible();
      const names = await collectionNames(page);
      expect(names.filter((n) => n.trim().toLowerCase() === 'kurs / kapitel 1')).toEqual(['Kurs / Kapitel 1']);
      expect(names.filter((n) => n.trim().toLowerCase() === 'kurs')).toEqual(['Kurs']);
    });

    await test.step('Тогда на экране тем по одной плитке на имя', async () => {
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await expect(page.getByRole('button', { name: 'Kurs 4', exact: true })).toHaveCount(1);
      await expect(page.getByRole('button', { name: 'Kapitel 1 2', exact: true })).toHaveCount(1);
    });
  });

  // The issue's repro (#460): the collections icon is clicked while the caret
  // is still in the heading. The click's mousedown takes focus, so the rename
  // starts on that blur and the themes screen opens before its write lands.
  test('пользователь переименовал тему и сразу открыл экран тем → новое имя уже видно', async ({ page }) => {
    await test.step('Дано открыта тема «xxx, aaa»', async () => {
      await page.goto('/');
      await seedCollections(page, [['xa', 'xxx, aaa', []]]);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await page.getByRole('button', { name: 'xxx, aaa 0', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'xxx, aaa' })).toBeVisible();
    });

    await test.step('Когда он вписывает в заголовок «xxx / aaa» и сразу жмёт значок тем', async () => {
      await page.getByRole('heading', { name: 'xxx, aaa' }).click();
      await page.keyboard.press('ControlOrMeta+a');
      await page.keyboard.type('xxx / aaa');
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Тогда видны папка «xxx» и тема «aaa», старого имени нет', async () => {
      await expect(page.getByRole('heading', { name: 'xxx 0', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'aaa 0', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: /xxx, aaa/ })).toHaveCount(0);
    });
  });

  test('пользователь нажимает на строку папки → открывается эта тема', async ({ page }) => {
    await test.step('Дано экран тем с папкой «Kurs»', async () => {
      await page.goto('/');
      await seedCollections(page, course);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Когда он нажимает «Kapitel 1»', async () => {
      await page.getByRole('button', { name: 'Kapitel 1 2', exact: true }).click();
    });

    await test.step('Тогда открыта тема «Kurs / Kapitel 1»', async () => {
      await expect(page.getByRole('heading', { name: 'Kurs / Kapitel 1' })).toBeVisible();
    });
  });

  test('пользователь выбирает родительскую тему и открывает список слов → в нём слова дочерних тем', async ({ page }) => {
    await test.step('Дано выбрана тема «Kurs» с дочерними', async () => {
      await page.goto('/');
      await seedWords(page, ['a', 'b', 'c', 'd', 'e', 'f']);
      await seedCollections(page, course);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await page.getByRole('button', { name: 'Kurs 4', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Kurs' })).toBeVisible();
    });

    await test.step('Когда он открывает список слов', async () => {
      await page.goto('/words');
      await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible();
    });

    await test.step('Тогда видны слова a, b, c, d, а слова e и f из «Grammatik» — нет', async () => {
      // Kurs holds a and b; its chapters add c and d; e and f are Grammatik's.
      for (const value of ['a', 'b', 'c', 'd']) {
        await expect(page.getByText(value, { exact: true })).toBeVisible();
      }
      await expect(page.getByText('e', { exact: true })).toHaveCount(0);
      await expect(page.getByText('f', { exact: true })).toHaveCount(0);
    });
  });

  test('пользователь печатает часть названия на экране тем → фокус на первой подходящей теме', async ({ page }) => {
    await test.step('Дано экран тем с папкой «Kurs» и темой «Solo»', async () => {
      await page.goto('/');
      await seedCollections(page, [...course, ['solo', 'Solo', ['vocab:g']]]);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await expect(page.getByRole('button', { name: 'Solo 1', exact: true })).toBeVisible();
    });

    await test.step('Когда он печатает «pit»', async () => {
      await page.keyboard.type('pit');
    });

    await test.step('Тогда фокус на «Kapitel 1»', async () => {
      await expect(page.getByRole('button', { name: 'Kapitel 1 2', exact: true })).toBeFocused();
    });

    await test.step('Когда он без паузы допечатывает «el 2»', async () => {
      await page.keyboard.type('el 2');
    });

    await test.step('Тогда фокус на «Kapitel 2»', async () => {
      await expect(page.getByRole('button', { name: 'Kapitel 2 1', exact: true })).toBeFocused();
    });

    await test.step('Когда он ждёт дольше секунды и печатает «so»', async () => {
      await nothingHappensFor(page, 1200);
      await page.keyboard.type('so');
    });

    await test.step('Тогда фокус на «Solo»', async () => {
      await expect(page.getByRole('button', { name: 'Solo 1', exact: true })).toBeFocused();
    });

    await test.step('Когда он возвращается на главную и печатает в поле слова', async () => {
      await page.keyboard.press('Escape');
      await page.getByLabel('Слово (немецкий)').focus();
      await page.keyboard.type('Haus');
    });

    await test.step('Тогда слово напечатано', async () => {
      await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('Haus');
    });
  });
});
