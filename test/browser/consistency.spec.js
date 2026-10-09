const { test, expect, nothingHappensFor, memoryReady } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// The learner's data in memory stays a projection of PouchDB (#494,
// ADR-0016). A screen reads it when opened and does not follow it while open:
// the app's own write shows at once, another tab's and a replicated one on
// the next opening. The app opens on a splash until the words and collections
// are read, and a screen left does not come back.

const rows = (page) => page.locator('.word-item');
const homeHeading = (page) => page.getByRole('heading', { name: 'Главная' });

async function reopenWords(page) {
  await page.getByRole('button', { name: 'Закрыть' }).click();
  await expect(page).toHaveURL(/\/home$/);
  await page.getByRole('button', { name: 'Список слов' }).click();
}

// Watches the page from before the app boots: whether either empty state of
// the words screen was ever on display — a claim shown for a moment is
// caught.
const watchStartup = `
  window.__claimed = [];
  new MutationObserver(() => {
    if (!document.body) return;
    for (const text of ['Слов пока нет', 'Ничего не найдено']) {
      if (document.body.textContent.includes(text) && !window.__claimed.includes(text)) {
        window.__claimed.push(text);
      }
    }
  }).observe(document, { childList: true, subtree: true, characterData: true });
`;

async function seedWords(page, n) {
  await page.evaluate(async (n) => {
    const now = new Date().toISOString();
    const docs = [];
    for (let i = 0; i < n; i++) {
      docs.push({ _id: 'vocab:wort' + (1000 + i), type: 'vocab', value: 'Wort' + (1000 + i), translation: [{ lang: 'ru', value: 'слово' }], created_at: now, modified_at: now });
    }
    await db.use('user-db').bulkDocs(docs);
  }, n);
}

test.describe('Согласованность списка слов', () => {
  test('пользователь добавляет слово и сразу открывает список → слово уже в нём', async ({ page }) => {
    await test.step('Дано главная с только что добавленным словом «Haus»', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'Haus', 'дом');
    });

    let shown;
    await test.step('Когда он открывает список слов', async () => {
      // Read in the first frame after the tap: the rows are there at once.
      shown = await page.evaluate(() => new Promise((resolve) => {
        requestAnimationFrame(() => resolve(
          [...document.querySelectorAll('.word-item__value')].map((n) => n.textContent),
        ));
        document.querySelector('#home-words-button').click();
      }));
    });

    await test.step('Тогда в первом же кадре виден «Haus»', async () => {
      expect(shown).toEqual(['Haus']);
    });
  });

  test('пользователь добавляет слово в другой вкладке → в открытом списке оно появляется после повторного открытия', async ({ context }) => {
    const reader = await context.newPage();
    const writer = await context.newPage();

    await test.step('Дано в первой вкладке открыт список с одним словом', async () => {
      await reader.goto('/home');
      await memoryReady(reader);
      await addWord(reader, 'Haus', 'дом');
      await reader.getByRole('button', { name: 'Список слов' }).click();
      await expect(rows(reader)).toHaveCount(1);
    });

    await test.step('Когда во второй вкладке он добавляет «Hund»', async () => {
      await writer.goto('/home');
      await memoryReady(writer);
      await addWord(writer, 'Hund', 'пёс');
    });

    await test.step('Тогда открытый список не меняется', async () => {
      await nothingHappensFor(reader, 1000);
      await expect(rows(reader)).toHaveCount(1);
    });

    await test.step('Когда он закрывает список и открывает снова', async () => {
      await reopenWords(reader);
    });

    await test.step('Тогда в списке два слова, среди них «Hund»', async () => {
      await expect(rows(reader).filter({ hasText: 'Hund' })).toBeVisible();
      await expect(rows(reader)).toHaveCount(2);
    });
  });

  test('слово приходит с другого устройства → в открытом списке оно появляется после повторного открытия', async ({ page }) => {
    await test.step('Дано открыт список с одним словом', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'Haus', 'дом');
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(rows(page)).toHaveCount(1);
    });

    await test.step('Когда с другого устройства приходит «Katze»', async () => {
      // Another device's database, replicated into this one the way a sync
      // pull writes: through PouchDB's replicator, not through the app.
      await page.evaluate(async () => {
        const other = db.use('another-device');
        await other.put({
          _id: 'vocab:katze', type: 'vocab', value: 'Katze',
          translation: [{ lang: 'ru', value: 'кошка' }],
          created_at: new Date().toISOString(), modified_at: new Date().toISOString(),
        });
        await other.constructor.replicate(other, db.use('user-db'));
        await other.destroy();
      });
    });

    await test.step('Тогда открытый список не меняется', async () => {
      await nothingHappensFor(page, 1000);
      await expect(rows(page)).toHaveCount(1);
    });

    await test.step('Когда он закрывает список и открывает снова', async () => {
      await reopenWords(page);
    });

    await test.step('Тогда в списке два слова, среди них «Katze»', async () => {
      await expect(rows(page).filter({ hasText: 'Katze' })).toBeVisible();
      await expect(rows(page)).toHaveCount(2);
    });
  });

  test('пользователь открывает и тут же закрывает список слов → список не возвращается поверх главной', async ({ page }) => {
    await test.step('Дано главная со словом', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'Haus', 'дом');
    });

    await test.step('Когда он открывает список и закрывает его в тот же миг', async () => {
      // #486: the close in the same task as the open, before anything the open
      // started could land.
      await page.evaluate(() => {
        document.querySelector('#home-words-button').click();
        document.querySelector('button[aria-label="Закрыть"]').click();
      });
    });

    await test.step('Тогда он на главной', async () => {
      await expect(homeHeading(page)).toBeAttached();
      await expect(page).toHaveURL(/\/home$/);
    });

    await test.step('Тогда и позже список не появляется поверх главной', async () => {
      await nothingHappensFor(page, 1500);
      await expect(rows(page)).toHaveCount(0);
      await expect(page).toHaveURL(/\/home$/);
      await expect(homeHeading(page)).toBeAttached();
    });
  });

  test('пользователь открывает приложение сразу на списке слов → видит слова, ни разу не «Слов пока нет»', async ({ page }) => {
    await test.step('Дано 2000 слов в словаре', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await seedWords(page, 2000);
    });

    await test.step('Когда он открывает приложение по адресу /words', async () => {
      await page.addInitScript(watchStartup);
      await page.goto('/words');
    });

    await test.step('Тогда видны слова, а пустых состояний не мелькало', async () => {
      await expect(rows(page).first()).toBeVisible({ timeout: 30000 });
      await expect(page).toHaveURL(/\/words$/);
      expect(await page.evaluate(() => window.__claimed)).toEqual([]);
    });
  });

  test('пользователь открывает пустой словарь, а затем ищет несуществующее → видит разные сообщения', async ({ page }) => {
    await test.step('Когда он открывает пустой список слов', async () => {
      await page.goto('/words');
      await memoryReady(page);
    });

    await test.step('Тогда видно «Слов пока нет» и нет поля поиска', async () => {
      await expect(page.getByText('Слов пока нет')).toBeVisible();
      await expect(page.getByPlaceholder('Поиск')).toHaveCount(0);
    });

    await test.step('Когда он добавляет слово и ищет «zzz»', async () => {
      await page.getByRole('button', { name: 'Добавить слово' }).click();
      await addWord(page, 'Haus', 'дом');
      await page.getByRole('button', { name: 'Список слов' }).click();
      await page.getByPlaceholder('Поиск').fill('zzz');
    });

    await test.step('Тогда видно «Ничего не найдено», а не «Слов пока нет»', async () => {
      await expect(page.getByText('Ничего не найдено')).toBeVisible();
      await expect(page.getByText('Слов пока нет')).toHaveCount(0);
      await expect(page.getByPlaceholder('Поиск')).toHaveValue('zzz');
    });
  });

  // The active collection is the one memory holds under the remembered id
  // (collections-data-model). An id it holds nothing under — the collection
  // was deleted on another device — acts as «Всё подряд» on every path, and
  // nothing clears it.
  test('удалённая на другом устройстве тема всё ещё запомнена → везде работает как «Всё подряд»', async ({ page }) => {
    await test.step('Дано запомненная тема, которой больше нет, и слово «Haus»', async () => {
      await page.addInitScript(() => localStorage.setItem('active-collection-id', 'collection:gone'));
      await page.goto('/home');
      await memoryReady(page);
      await expect(homeHeading(page)).toBeVisible();
      await addWord(page, 'Haus', 'дом');
    });

    await test.step('Тогда список слов показывает слово', async () => {
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(rows(page)).toHaveCount(1);
    });

    await test.step('Когда он пробует удалить слово', async () => {
      let prompt = null;
      page.once('dialog', (dialog) => { prompt = dialog.message(); dialog.dismiss(); });
      await page.locator('.word-item__display').first().click();
      await page.locator('.word-edit-dialog__delete').click();
      await expect.poll(() => prompt).toBe('Удалить «Haus» окончательно?');
      await page.locator('.word-edit-dialog__cancel').click();
    });

    await test.step('Тогда урок начинается со словом из словаря', async () => {
      await page.getByRole('button', { name: 'Закрыть' }).click();
      await page.locator('.home__lesson-button').click();
      await expect(page.locator('.lesson__prompt')).toBeVisible();
    });

    await test.step('Тогда на экране тем выбрано «Всё подряд», запомненный id не тронут', async () => {
      await page.goto('/collections');
      await expect(page.getByRole('button', { name: 'Всё подряд 1', exact: true })).toHaveAttribute('aria-current', 'true');
      expect(await page.evaluate(() => localStorage.getItem('active-collection-id'))).toBe('collection:gone');
    });
  });
});
