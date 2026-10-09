const { test, expect, nothingHappensFor } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// A screen renders in the task of the tap that opened it (#494), so the
// second click of a double click lands on the new screen. It must not act
// there: a double click is one activation of the control it started on.
// Likewise one Enter on the focused ДАЛЕЕ advances the lesson once (#277): the
// button used to click itself on top of the native Enter activation.

// Read at the engine level, as test/browser/README.md prescribes.
async function reviewCount(page) {
  return page.evaluate(async () => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    const found = await db.find_all(db.use('user-db'), toClj({ selector: { type: 'review' } }));
    return cljs.core.count(cljs.core.get(found, kw('docs')));
  });
}

const answerWrong = async (page) => {
  await page.locator('#lesson-answer').fill('falsch');
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await expect(page.getByRole('button', { name: 'ДАЛЕЕ' })).toBeFocused();
};

// Counts the clicks that reach ДАЛЕЕ: a second activation is invisible on
// screen once the next trial has replaced the button.
const countNextClicks = (page) =>
  page.evaluate(() => {
    window.__continueClicks = 0;
    document.addEventListener('click', (event) => {
      if (event.target.id === 'lesson-next') window.__continueClicks += 1;
    }, true);
  });

const nextClicks = (page) => page.evaluate(() => window.__continueClicks);

test.describe('Двойное нажатие', () => {
  test('пользователь дважды нажимает кнопку или Enter → выполняется одно действие', async ({ page }) => {
    const lessonErrors = [];
    page.on('console', (message) => {
      if (message.type() === 'error' && message.text().includes('use-cases.lesson')) {
        lessonErrors.push(message.text());
      }
    });
    const answer = page.locator('#lesson-answer');

    await test.step('Дано урок из трёх слов, неверный ответ, фокус на «ДАЛЕЕ»', async () => {
      await page.goto('/home');
      await addWord(page, 'der Hund', 'пёс');
      await addWord(page, 'die Katze', 'кошка');
      await addWord(page, 'das Haus', 'дом');
      await page.goto('/lesson');
      await expect(page.locator('.lesson__prompt')).toBeVisible();
      await countNextClicks(page);
      await answerWrong(page);
    });

    await test.step('Когда он жмёт Enter на «ДАЛЕЕ»', async () => {
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда урок перешёл на следующее задание один раз', async () => {
      await expect(answer).toBeVisible();
      await nothingHappensFor(page, 1000);
      expect(await nextClicks(page)).toBe(1);
      expect(lessonErrors).toEqual([]);
    });

    await test.step('Когда он отвечает неверно и дважды кликает «ДАЛЕЕ»', async () => {
      await answerWrong(page);
      await page.getByRole('button', { name: 'ДАЛЕЕ' }).dblclick();
    });

    await test.step('Тогда переход один, а следующий ответ не проверен', async () => {
      await expect(answer).toBeVisible();
      await nothingHappensFor(page, 1000);
      expect(await nextClicks(page)).toBe(2);
      await expect(page.getByRole('heading', { name: 'Ваш ответ:' })).toHaveCount(0);
      expect(lessonErrors).toEqual([]);
    });

    await test.step('Когда он открывает список слов и дважды кликает угловой ✕', async () => {
      await page.goto('/home');
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(page.locator('.word-item')).toHaveCount(3);
      await page.getByRole('button', { name: 'Закрыть' }).dblclick();
    });

    await test.step('Тогда он на главной, и ничего другого не открылось', async () => {
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
      await nothingHappensFor(page, 1000);
      await expect(page).toHaveURL(/\/home$/);
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
    });
  });

  test('пользователь дважды кликает «НАЧАТЬ УРОК» → урок начат, ответ не проверен', async ({ page }) => {
    let before;

    await test.step('Дано главная с двумя словами', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await addWord(page, 'Hund', 'пёс');
      before = await reviewCount(page);
    });

    await test.step('Когда он дважды кликает «НАЧАТЬ УРОК»', async () => {
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).dblclick();
    });

    await test.step('Тогда открыт урок с пустым полем ответа, ничего не проверено', async () => {
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(page.locator('#lesson-answer')).toBeVisible();
      await nothingHappensFor(page, 1000);
      await expect(page.getByRole('heading', { name: 'Ваш ответ:' })).toHaveCount(0);
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toHaveCount(0);
      expect(await reviewCount(page)).toBe(before);
    });
  });

  test('пользователь жмёт «ПРОВЕРИТЬ» с пустым ответом → ответ не проверяется', async ({ page }) => {
    let before;

    await test.step('Дано урок с пустым полем ответа', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
      await expect(page.locator('#lesson-answer')).toBeVisible();
      before = await reviewCount(page);
    });

    await test.step('Когда он жмёт «ПРОВЕРИТЬ»', async () => {
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
    });

    await test.step('Тогда поле ответа на месте, результата нет', async () => {
      await nothingHappensFor(page, 1000);
      await expect(page.locator('#lesson-answer')).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Ваш ответ:' })).toHaveCount(0);
      expect(await reviewCount(page)).toBe(before);
    });
  });
});
