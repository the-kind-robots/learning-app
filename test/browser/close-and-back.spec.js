const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// Every screen but home closes from the corner, and home has no app screen
// behind it (#411, ADR-0015).

const close = (page) => page.getByRole('button', { name: 'Закрыть' });
const homeHeading = (page) => page.getByRole('heading', { name: 'Главная' });

// Waits for the rows, not only the address: a words read still in flight
// when the test leaves would land after home's and put the words screen back
// on display at /home — a race in the page loads, not in the history.
async function openWords(page) {
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page).toHaveURL(/\/words$/);
  await expect(page.getByRole('listitem').filter({ hasText: 'Haus' })).toBeVisible();
}

// A page of the same origin before the app, so Back from home has somewhere
// outside the app to land. Chrome replaces the initial about:blank entry, so
// that one would not do.
async function openAppAfterAnotherPage(page) {
  await page.goto('/favicon.ico');
  await page.goto('/home');
  await expect(homeHeading(page)).toBeVisible();
}

test.describe('Закрытие экранов и кнопка «Назад»', () => {
  test('пользователь открывает экраны → у главной нет ✕, у слов, тем и урока он есть и ведёт на главную', async ({ page }) => {
    await test.step('Дано главная со словом «Haus»', async () => {
      await page.goto('/home');
      await expect(homeHeading(page)).toBeVisible();
      await expect(page.getByRole('button', { name: 'Открыть наборы' })).toBeVisible();
      await addWord(page, 'Haus', 'дом');
    });

    await test.step('Тогда на главной нет ✕', async () => {
      await expect(close(page)).toHaveCount(0);
    });

    await test.step('Когда он открывает список слов', async () => {
      await page.getByRole('button', { name: 'Список слов' }).click();
    });

    await test.step('Тогда есть один ✕, и он ведёт на главную', async () => {
      await expect(page.getByPlaceholder('Поиск')).toBeVisible();
      await expect(close(page)).toHaveCount(1);
      await close(page).click();
      await expect(homeHeading(page)).toBeVisible();
    });

    await test.step('Когда он открывает наборы', async () => {
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Тогда есть один ✕, и он ведёт на главную', async () => {
      await expect(page).toHaveURL(/\/collections$/);
      await expect(close(page)).toHaveCount(1);
      await close(page).click();
      await expect(homeHeading(page)).toBeVisible();
    });

    await test.step('Когда он начинает урок', async () => {
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
    });

    await test.step('Тогда есть один ✕, и он ведёт на главную', async () => {
      await expect(page.getByRole('progressbar', { name: 'Прогресс урока' })).toBeVisible();
      await expect(close(page)).toHaveCount(1);
      await expect(page.getByRole('button', { name: 'Закрыть урок' })).toHaveCount(0);
      await close(page).click();
      await expect(homeHeading(page)).toBeVisible();
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('пользователь закрывает экран или жмёт «Назад» → под любым экраном только главная, а за ней страница до приложения', async ({ page }) => {
    await test.step('Дано главная, открытая после другой страницы, и слово «Haus»', async () => {
      await openAppAfterAnotherPage(page);
      await addWord(page, 'Haus', 'дом');
    });

    await test.step('Когда он открывает слова и закрывает ✕', async () => {
      await openWords(page);
      await close(page).click();
    });

    await test.step('Тогда он на главной, а «Назад» уводит из приложения', async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(homeHeading(page)).toBeVisible();
      await page.goBack();
      await expect(page).toHaveURL(/\/favicon\.ico$/);
    });

    await test.step('Когда он снова входит, открывает слова и жмёт «Назад»', async () => {
      await page.goto('/home');
      await openWords(page);
      await page.goBack();
    });

    await test.step('Тогда он на главной, и ещё один «Назад» выходит из приложения', async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(homeHeading(page)).toBeVisible();
      await page.goBack();
      await expect(page).toHaveURL(/\/favicon\.ico$/);
    });

    await test.step('Когда он снова входит, открывает слова и из них начинает урок', async () => {
      await page.goto('/home');
      await openWords(page);
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(page.getByRole('progressbar', { name: 'Прогресс урока' })).toBeVisible();
    });

    await test.step('Тогда урок занял место слов: «Назад» ведёт на главную', async () => {
      await page.goBack();
      await expect(page).toHaveURL(/\/home$/);
      await expect(homeHeading(page)).toBeVisible();
    });
  });

  test('пользователь открывает экран по прямой ссылке → под ним главная, перезагрузка ничего не добавляет', async ({ page }) => {
    await test.step('Дано страница до приложения, затем прямая ссылка на слова', async () => {
      await page.goto('/favicon.ico');
      await page.goto('/words');
      await expect(page).toHaveURL(/\/words$/);
      await expect(page.getByText('Слов пока нет')).toBeVisible();
      await expect(close(page)).toHaveCount(1);
    });

    await test.step('Когда он перезагружает страницу', async () => {
      // A reload finds the entry already standing on home and adds nothing.
      await page.reload();
    });

    await test.step('Тогда экран слов на месте', async () => {
      await expect(page.getByText('Слов пока нет')).toBeVisible();
      await expect(close(page)).toHaveCount(1);
    });

    await test.step('Когда он жмёт «Назад»', async () => {
      await page.goBack();
    });

    await test.step('Тогда он на главной, а ещё один «Назад» выходит из приложения', async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(homeHeading(page)).toBeVisible();
      await page.goBack();
      await expect(page).toHaveURL(/\/favicon\.ico$/);
    });
  });
});

// Leaving a lesson ends it, whatever did the leaving: Back, the corner close,
// the finish button (#484); entering again starts fresh. The lesson lives in
// app state only, so no lesson document is ever written — read at the engine
// level, as test/browser/README.md prescribes.
async function storedLessons(page) {
  return page.evaluate(async () => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    const found = await db.find(db.use('device-db'), toClj({ selector: { type: 'lesson' } }));
    return cljs.core.count(cljs.core.get(found, kw('docs')));
  });
}

const ANSWERS = { дом: 'Haus', собака: 'Hund' };

async function addWords(page) {
  for (const [translation, word] of Object.entries(ANSWERS)) {
    await addWord(page, word, translation);
  }
}

const progress = (page) => page.getByRole('progressbar', { name: 'Прогресс урока' });

async function startLesson(page) {
  await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
  await expect(page).toHaveURL(/\/lesson$/);
  await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
}

// The answer field is on screen only while a trial waits for its answer, so
// the prompt read after it is the current trial's, not the one just passed.
async function answerCorrectly(page) {
  await expect(page.locator('#lesson-answer')).toBeVisible();
  const prompt = (await page.locator('.lesson__prompt').textContent()).trim();
  await page.locator('#lesson-answer').fill(ANSWERS[prompt]);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
}

test.describe('Выход из урока', () => {
  test('пользователь выходит из урока кнопкой «Назад» → урок закончен, новый начинается заново', async ({ page }) => {
    await test.step('Дано урок, в котором он ответил на первое задание и перешёл дальше', async () => {
      await page.goto('/home');
      await addWords(page);
      await startLesson(page);
      await answerCorrectly(page);
      await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
      await expect(progress(page)).not.toHaveAttribute('aria-valuenow', '0');
    });

    await test.step('Когда он жмёт «Назад»', async () => {
      await page.goBack();
    });

    await test.step('Тогда он на главной, а урок нигде не сохранён', async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(homeHeading(page)).toBeVisible();
      expect(await storedLessons(page)).toBe(0);
    });

    await test.step('Тогда новый урок начинается с нулевого прогресса', async () => {
      await startLesson(page);
    });
  });

  test('пользователь закрывает урок угловым ✕ → урок закончен, он на главной', async ({ page }) => {
    await test.step('Дано урок с одним отвеченным заданием', async () => {
      await page.goto('/home');
      await addWords(page);
      await startLesson(page);
      await answerCorrectly(page);
    });

    await test.step('Когда он жмёт ✕', async () => {
      await close(page).click();
    });

    await test.step('Тогда он на главной, а урок нигде не сохранён', async () => {
      await expect(homeHeading(page)).toBeVisible();
      expect(await storedLessons(page)).toBe(0);
    });
  });

  test('пользователь заканчивает урок → он на главной, новый урок начинается заново', async ({ page }) => {
    await test.step('Дано урок с двумя отвеченными заданиями', async () => {
      await page.goto('/home');
      await addWords(page);
      await startLesson(page);
      await answerCorrectly(page);
      await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
      await answerCorrectly(page);
    });

    await test.step('Когда он жмёт «ЗАКОНЧИТЬ»', async () => {
      await page.getByRole('button', { name: 'ЗАКОНЧИТЬ' }).click();
    });

    await test.step('Тогда он на главной, а урок нигде не сохранён', async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(homeHeading(page)).toBeVisible();
      expect(await storedLessons(page)).toBe(0);
    });

    await test.step('Тогда новый урок начинается с нулевого прогресса', async () => {
      await startLesson(page);
    });
  });
});
