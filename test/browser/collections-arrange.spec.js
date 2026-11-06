const { test, expect, nothingHappensFor } = require('./fixtures');

// Seeded at the engine level, all made at once and never moved: such tiles stand alphabetically.
async function seedCollections(page, collections) {
  await page.evaluate(async (collections) => {
    const now = new Date().toISOString();
    const docs = collections.map(([id, name, wordIds]) => ({
      _id: 'collection:' + id, type: 'collection', name, word_ids: wordIds, created_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, collections);
}

const abc = [
  ['a', 'A', ['vocab:a']],
  ['b', 'B', ['vocab:b']],
  ['c', 'C', ['vocab:c']],
];

// The name each tile shows, in the order the screen reads.
const tileNames = (page) =>
  page.locator('.masonry > .tile > :first-child').locator('.tile__name, .tile__label-text');

async function openCollections(page) {
  await page.getByRole('button', { name: 'Открыть наборы' }).click();
  await expect(page.getByRole('button', { name: /^Всё подряд/ })).toBeVisible();
}

// Pressed on `from`, carried in steps and let go just inside the top of `onto`.
async function dragAbove(page, from, onto) {
  const start = await from.boundingBox();
  const target = await onto.boundingBox();
  await page.mouse.move(start.x + start.width / 2, start.y + start.height / 2);
  await page.mouse.down();
  await page.mouse.move(target.x + target.width / 2, target.y + 4, { steps: 12 });
  await page.mouse.up();
}

const accentsByName = (page) =>
  page.locator('.masonry > .tile').evaluateAll((tiles) => Object.fromEntries(tiles.map((tile) => [
    tile.querySelector('.tile__name, .tile__label-text').textContent,
    getComputedStyle(tile).getPropertyValue('--accent').replace(/\s/g, ''),
  ])));

test.describe('Порядок тем', () => {
  test('пользователь перетаскивает тему мышью выше другой → она стоит там и после перезагрузки', async ({ page }) => {
    await test.step('Дано темы «A», «B», «C» на экране тем', async () => {
      await page.goto('/');
      await seedCollections(page, abc);
      await openCollections(page);
      await expect(tileNames(page)).toHaveText(['Всё подряд', 'A', 'B', 'C']);
    });

    await test.step('Когда он тащит «C» мышью выше «A»', async () => {
      await dragAbove(page, page.getByRole('button', { name: 'C 1', exact: true }),
                      page.getByRole('button', { name: 'A 1', exact: true }));
    });

    await test.step('Тогда «C» стоит перед «A», и тема не открылась', async () => {
      await expect(tileNames(page)).toHaveText(['Всё подряд', 'C', 'A', 'B']);
      await expect(page.getByRole('heading', { name: 'Наборы' })).toBeAttached();
    });

    await test.step('Тогда после перезагрузки порядок тот же', async () => {
      await page.reload();
      await expect(tileNames(page)).toHaveText(['Всё подряд', 'C', 'A', 'B']);
    });
  });

  test('пользователь долго тащит тему мышью → она переезжает, а не открывается', async ({ page }) => {
    await test.step('Дано темы «A», «B», «C» на экране тем', async () => {
      await page.goto('/');
      await seedCollections(page, abc);
      await openCollections(page);
    });

    await test.step('Когда он тащит «C» выше «A», держит её там полторы секунды и отпускает', async () => {
      const start = await page.getByRole('button', { name: 'C 1', exact: true }).boundingBox();
      const target = await page.getByRole('button', { name: 'A 1', exact: true }).boundingBox();
      await page.mouse.move(start.x + start.width / 2, start.y + start.height / 2);
      await page.mouse.down();
      await page.mouse.move(target.x + target.width / 2, target.y + 4, { steps: 12 });
      // Longer than the second a drag's click guard used to last from the lift.
      await nothingHappensFor(page, 1500);
      await page.mouse.up();
    });

    await test.step('Тогда «C» стоит перед «A», и экран тем не закрылся', async () => {
      await expect(tileNames(page)).toHaveText(['Всё подряд', 'C', 'A', 'B']);
      await expect(page.getByRole('heading', { name: 'Наборы' })).toBeAttached();
    });
  });

  test('пользователь тащит строку папки → папка переезжает целиком', async ({ page }) => {
    await test.step('Дано папка «Kurs» с двумя главами и темы «Grammatik» и «Solo»', async () => {
      await page.goto('/');
      await seedCollections(page, [
        ['k1', 'Kurs / Kapitel 1', ['vocab:a']],
        ['k2', 'Kurs / Kapitel 2', ['vocab:b']],
        ['gram', 'Grammatik', ['vocab:c']],
        ['solo', 'Solo', ['vocab:d']],
      ]);
      await openCollections(page);
      await expect(tileNames(page)).toHaveText(['Всё подряд', 'Grammatik', 'Kurs', 'Solo']);
    });

    await test.step('Когда он тащит строку «Kapitel 2» выше «Grammatik»', async () => {
      await dragAbove(page, page.getByRole('button', { name: 'Kapitel 2 1', exact: true }),
                      page.getByRole('button', { name: 'Grammatik 1', exact: true }));
    });

    await test.step('Тогда папка стоит перед «Grammatik», её строки по-прежнему по порядку', async () => {
      await expect(tileNames(page)).toHaveText(['Всё подряд', 'Kurs', 'Grammatik', 'Solo']);
      await expect(page.locator('.tile__row-name')).toHaveText(['Kapitel 1', 'Kapitel 2']);
    });
  });

  test('пользователь тащит тему выше «Всё подряд» → «Всё подряд» остаётся первой', async ({ page }) => {
    await test.step('Дано темы «A», «B», «C» на экране тем', async () => {
      await page.goto('/');
      await seedCollections(page, abc);
      await openCollections(page);
    });

    await test.step('Когда он тащит «C» выше «Всё подряд»', async () => {
      await dragAbove(page, page.getByRole('button', { name: 'C 1', exact: true }),
                      page.getByRole('button', { name: /^Всё подряд/ }));
    });

    await test.step('Тогда первой стоит «Всё подряд», за ней «C»', async () => {
      await expect(tileNames(page)).toHaveText(['Всё подряд', 'C', 'A', 'B']);
    });
  });

  test('пользователь перетаскивает тему → каждая плитка сохраняет свой цвет', async ({ page }) => {
    let before;
    await test.step('Дано темы «A», «B», «C» на экране тем', async () => {
      await page.goto('/');
      await seedCollections(page, abc);
      await openCollections(page);
      before = await accentsByName(page);
    });

    await test.step('Когда он тащит «C» выше «A»', async () => {
      await dragAbove(page, page.getByRole('button', { name: 'C 1', exact: true }),
                      page.getByRole('button', { name: 'A 1', exact: true }));
      await expect(tileNames(page)).toHaveText(['Всё подряд', 'C', 'A', 'B']);
    });

    await test.step('Тогда у каждой плитки прежний цвет', async () => {
      expect(await accentsByName(page)).toEqual(before);
    });
  });

  test('пользователь создаёт тему → она встаёт в конец, а не по алфавиту', async ({ page }) => {
    await test.step('Дано темы «B» и «C» на экране тем', async () => {
      await page.goto('/');
      await seedCollections(page, abc.slice(1));
      await openCollections(page);
    });

    await test.step('Когда он создаёт тему «A» кнопкой «ДОБАВИТЬ НАБОР»', async () => {
      page.once('dialog', (dialog) => dialog.accept('A'));
      await page.getByRole('button', { name: 'ДОБАВИТЬ НАБОР' }).click();
    });

    await test.step('Тогда «A» стоит последней', async () => {
      await expect(tileNames(page)).toHaveText(['Всё подряд', 'B', 'C', 'A']);
    });
  });
});
