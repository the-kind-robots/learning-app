const { test, expect, nothingHappensFor } = require('./fixtures');

// The requirement counts row elements in the document, so the locator is the
// row class rather than a role: `listitem` also matches the end-of-list
// sentinel and the empty-state row, which are not words.
const rows = (page) => page.locator('li.word-item');
const sentinel = (page) => page.locator('li.word-list__sentinel');

const PAGE_SIZE = 50;
const SEEDED = 130;

// Seeded at the engine level (see README, "Seeding from a spec"): 130 words
// through the add form would be 130 dictionary round-trips, and the list under
// test does not care how a word got there. No reviews — every word then has
// the same retention level, so the retention sort keeps the seeded order.
async function seedWords(page, n) {
  await page.evaluate(async (count) => {
    const now = new Date().toISOString();
    const docs = [];
    for (let i = 0; i < count; i++) {
      const value = 'wort' + String(i).padStart(3, '0');
      docs.push({
        _id: 'vocab:' + value,
        type: 'vocab',
        value,
        translation: [{ lang: 'ru', value: 'слово' + i }],
        created_at: now,
        modified_at: now,
      });
    }
    await db.bulk_docs(db.use('user-db'), docs);
  }, n);
}

// A longer wait than the default: the screen shows "Загружаем..." until the
// app has opened its databases and read the seeded vocabulary, which on a busy
// machine has been measured past the 5 s default.
async function openWords(page) {
  await page.goto('/words');
  await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible({ timeout: 20000 });
}

// Reaching the bottom is a scroll, not a click: the list scrolls inside
// `.vocabulary__list`, so scrolling the last row into view is what a reader
// does and what the observer watches for.
async function scrollToBottom(page) {
  await rows(page).last().scrollIntoViewIfNeeded();
}

const listScrollTop = (page) =>
  page.locator('.vocabulary__list').evaluate((node) => node.scrollTop);

// Stops `gap` pixels short of the end of the loaded rows and reports, in the
// same task, where the sentinel sits relative to the visible bottom of the
// list. Nothing can re-layout in between — the observer answers in a later
// task — so a positive gap here is the sentinel being off screen at the moment
// the scroll happened.
const scrollToWithin = (page, gap) =>
  page.locator('.vocabulary__list').evaluate((list, gap) => {
    list.scrollTop = list.scrollHeight - list.clientHeight - gap;
    const sentinel = document.querySelector('li.word-list__sentinel');
    return sentinel.getBoundingClientRect().top - list.getBoundingClientRect().bottom;
  }, gap);

const seedAndOpen = async (page) => {
  await page.goto('/');
  await seedWords(page, SEEDED);
  await openWords(page);
};

test.describe('Постраничный список слов', () => {
  test('пользователь листает список к концу → следующая страница подгружается заранее, пока слова не кончатся', async ({ page }) => {
    await test.step('Дано 130 слов, открыт список', async () => {
      await seedAndOpen(page);
    });

    await test.step('Тогда показана одна страница из 50 слов', async () => {
      await expect(rows(page)).toHaveCount(PAGE_SIZE);
      await expect(sentinel(page)).toHaveCount(1);
    });

    await test.step('Когда он листает, не доходя до конца страницы', async () => {
      // GH-439: the observer's root has to be the box that clips the sentinel,
      // or the page was asked for only once the sentinel was already on screen.
      const gap = await scrollToWithin(page, 150);
      expect(gap).toBeGreaterThan(100);
    });

    await test.step('Тогда следующая страница уже добавлена к первой', async () => {
      await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
      await expect(rows(page).first()).toContainText('wort000');
    });

    await test.step('Когда он доходит до конца', async () => {
      await scrollToBottom(page);
    });

    await test.step('Тогда показаны все 130 слов, больше грузить нечего', async () => {
      await expect(rows(page)).toHaveCount(SEEDED);
      await expect(sentinel(page)).toHaveCount(0);
    });
  });

  test('пользователь ищет слово после прокрутки → поиск начинается с первой страницы сверху', async ({ page }) => {
    await test.step('Дано список, прокрученный до второй страницы', async () => {
      await seedAndOpen(page);
      await scrollToBottom(page);
      await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
    });

    await test.step('Когда он вводит запрос, подходящий ко всем словам', async () => {
      await page.getByPlaceholder('Поиск').fill('wort');
    });

    await test.step('Тогда показана одна страница, список прокручен наверх', async () => {
      await expect(rows(page)).toHaveCount(PAGE_SIZE);
      // At the bottom the reader would sit on the sentinel and the second
      // page would load itself.
      expect(await listScrollTop(page)).toBe(0);
    });

    await test.step('Когда он стирает запрос', async () => {
      await page.getByPlaceholder('Поиск').fill('');
    });

    await test.step('Тогда снова одна страница', async () => {
      await expect(rows(page)).toHaveCount(PAGE_SIZE);
    });
  });

  test('пользователь правит слово из подгруженных → правка видна, подгруженные слова на месте', async ({ page }) => {
    await test.step('Дано список с двумя страницами', async () => {
      await seedAndOpen(page);
      await scrollToBottom(page);
      await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
    });

    await test.step('Когда он правит перевод четвёртого слова и сохраняет', async () => {
      await rows(page).nth(3).getByRole('button').click();
      await page.getByRole('textbox', { name: 'Перевод' }).fill('исправленный перевод');
      await page.getByRole('button', { name: 'Сохранить' }).click();
    });

    await test.step('Тогда слово показывает новый перевод, окно закрыто, 100 слов на месте', async () => {
      await expect(rows(page).nth(3)).toContainText('исправленный перевод');
      await expect(page.locator('dialog.word-edit-dialog')).toHaveCount(0);
      await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
    });
  });

  // GH-439, then #494: a page of the replaced query used to land after the
  // matching rows and put the whole vocabulary back under a filled search box.
  test('пользователь доходит до конца сразу после запроса → результаты поиска не пропадают', async ({ page }) => {
    await test.step('Дано список с двумя страницами', async () => {
      await seedAndOpen(page);
      await scrollToBottom(page);
      await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
    });

    await test.step('Когда он вводит запрос на десять слов и сразу листает вниз', async () => {
      // 'wort01' matches wort010..wort019 — well under a page, so an
      // unfiltered page arriving afterwards is unmistakable.
      await page.getByPlaceholder('Поиск').fill('wort01');
      await expect(rows(page)).toHaveCount(10);
      expect(await listScrollTop(page)).toBe(0);
      await scrollToBottom(page);
    });

    await test.step('Тогда в списке по-прежнему только десять найденных слов', async () => {
      await expect(page.getByPlaceholder('Поиск')).toHaveValue('wort01');
      await expect(sentinel(page)).toHaveCount(0);
      await nothingHappensFor(page, 1500);
      await expect(rows(page)).toHaveCount(10);
    });
  });

  // GH-439: rows used to clear `:words/editing` on their way in, which became a
  // dialog shutting itself once the sentinel started asking for pages.
  test('пользователь правит слово, пока подгружается страница → окно правки не закрывается', async ({ page }) => {
    const translation = page.getByRole('textbox', { name: 'Перевод' });

    await test.step('Дано открытое окно правки с введённым переводом', async () => {
      await seedAndOpen(page);
      await rows(page).nth(3).getByRole('button').click();
      await translation.fill('печатаю прямо сейчас');
    });

    await test.step('Когда за окном подгружается следующая страница', async () => {
      // The dialog is modal, so the list behind it is inert to a click but not
      // to a scroll driven from script.
      await scrollToWithin(page, 150);
      await expect(rows(page)).toHaveCount(2 * PAGE_SIZE);
    });

    await test.step('Тогда окно открыто, а введённое осталось', async () => {
      await expect(page.locator('dialog.word-edit-dialog')).toBeVisible();
      await expect(translation).toHaveValue('печатаю прямо сейчас');
    });
  });
});
