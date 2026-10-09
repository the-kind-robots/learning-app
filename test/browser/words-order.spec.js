const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// The list is ordered and paged off the view's key, so what a page holds and
// in what order is one question. Rows are located by their class for the same
// reason `words-paging.spec.js` does it: `listitem` also matches the sentinel.
const rows = (page) => page.locator('li.word-item');

// The issue's own six words. `aufstehen` before `das Auto` — `auf` sorts
// before `aut`, whatever the issue's illustration says; the article is what is
// under test, and it puts Auto under A, Bank under B and Zug under Z.
const SIX = ['der Hund', 'die Katze', 'das Auto', 'der Zug', 'die Bank', 'aufstehen'];
const FILED = ['aufstehen', 'das Auto', 'die Bank', 'der Hund', 'die Katze', 'der Zug'];

const PAGE_SIZE = 50;

// Seeded at the engine level (see README, "Seeding from a spec"). The id is
// what the app itself would store the value under — "vocab:" plus the
// normalised value, article included — because that is the defect: the id
// keeps the article and the list used to be ordered by it.
async function seed(page, values) {
  await page.evaluate(async (vals) => {
    const now = new Date().toISOString();
    const docs = vals.map((value, i) => ({
      _id: 'vocab:' + value.toLowerCase(),
      type: 'vocab',
      value,
      translation: [{ lang: 'ru', value: 'перевод' + i }],
      created_at: now,
      modified_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, values);
}

async function openWords(page) {
  await page.goto('/words');
  await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible({ timeout: 20000 });
}

// 60 nouns whose articles cycle, so ordering by the stored id would put all
// twenty `das` words first and all twenty `die` words last. Ordering by the
// word puts them in numeric order, and the page boundary at 50 is where a sort
// done after the page was cut would show: it can only order what it was given.
const CYCLED = Array.from({ length: 60 }, (_, i) => {
  const article = ['der', 'die', 'das'][i % 3];
  return article + ' Wort' + String(i).padStart(3, '0');
});

test.describe('Порядок слов в списке', () => {
  test('пользователь открывает список слов → существительное стоит по слову, а не по артиклю', async ({ page }) => {
    await test.step('Дано шесть слов, среди них существительные с артиклями', async () => {
      await page.goto('/');
      await seed(page, SIX);
    });

    await test.step('Когда он открывает список слов', async () => {
      await openWords(page);
    });

    await test.step('Тогда слова идут по алфавиту без учёта артикля', async () => {
      await expect(rows(page)).toHaveCount(SIX.length);
      await expect(rows(page)).toHaveText(FILED.map((value) => new RegExp(value)));
    });
  });

  test('пользователь прокручивает список дальше первой страницы → порядок продолжается без разрывов', async ({ page }) => {
    await test.step('Дано шестьдесят слов с чередующимися артиклями', async () => {
      await page.goto('/');
      await seed(page, CYCLED);
      await openWords(page);
    });

    await test.step('Тогда показана первая страница от «der Wort000» до «die Wort049»', async () => {
      await expect(rows(page)).toHaveCount(PAGE_SIZE);
      await expect(rows(page).first()).toContainText('der Wort000');
      await expect(rows(page).nth(PAGE_SIZE - 1)).toContainText('die Wort049');
    });

    await test.step('Когда он доходит до конца списка', async () => {
      await rows(page).last().scrollIntoViewIfNeeded();
    });

    await test.step('Тогда подгружены все слова подряд, без пропусков и повторов', async () => {
      await expect(rows(page)).toHaveCount(CYCLED.length);
      await expect(rows(page).nth(PAGE_SIZE)).toContainText('das Wort050');
      await expect(rows(page).last()).toContainText('das Wort059');
      const rendered = await rows(page).allTextContents();
      const numbers = rendered.map((text) => Number(text.match(/Wort(\d{3})/)[1]));
      expect(numbers).toEqual(numbers.map((_, i) => i));
    });
  });

  test('пользователь добавляет то же слово с артиклем дважды и правит его → остаётся одна запись', async ({ page }) => {
    await test.step('Когда он добавляет «der Zug» дважды с разными переводами', async () => {
      await page.goto('/home');
      // Through the add form, so the whole duplicate check runs.
      await addWord(page, 'der Zug', 'поезд');
      await addWord(page, 'der Zug', 'состав');
    });

    await test.step('Тогда в списке одна запись с обоими переводами', async () => {
      await openWords(page);
      await expect(rows(page)).toHaveCount(1);
      await expect(rows(page).first()).toContainText('der Zug');
      await expect(rows(page).first()).toContainText('поезд');
      await expect(rows(page).first()).toContainText('состав');
    });

    await test.step('Когда он правит перевод', async () => {
      await rows(page).first().getByRole('button').click();
      await page.getByRole('textbox', { name: 'Перевод' }).fill('поезд, состав');
      await page.getByRole('button', { name: 'Сохранить' }).click();
    });

    await test.step('Тогда запись обновилась, а новой не появилось', async () => {
      await expect(rows(page).first()).toContainText('поезд, состав');
      await expect(rows(page)).toHaveCount(1);
    });
  });
});
