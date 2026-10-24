const { test, expect, docsOfType } = require('./fixtures');
const { BARE, EXAMPLES, SEEDED, THEMES, TRIP, card, openWord, seedWord } = require('./word-example.shared');

// GH-598: the word dialog shows the example of every collection of the word on cards, each written in
// place, and asks for another one on request. The endpoint is stubbed with `page.route`.

const ANOTHER = 'Der Bahnhof liegt direkt im Zentrum.';

// Answers every POST to the endpoint with `respond`; returns the bodies it was asked with.
async function stubRegeneration(page, respond) {
  const asked = [];
  await page.route('**/api/examples', async (route) => {
    asked.push(route.request().postDataJSON());
    await respond(route);
  });
  return asked;
}

const answer = (value, translation) => (route) =>
  route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ value, translation }) });

const sentence = (region) => region.getByLabel('Пример', { exact: true });
const translation = (region) => region.getByLabel('Перевод примера');
const save = (page) => page.getByRole('button', { name: 'Сохранить' });
const reset = (page) => page.getByRole('button', { name: 'Сбросить' });

test.describe('Примеры в окне слова', () => {
  test('пользователь открывает слово → видит карточку каждой темы слова, слово в предложении выделено', async ({ page }) => {
    await test.step('Дано слово в темах «Reisen/Bahn» и «Alltag» и пример без темы', async () => {
      await seedWord(page, { collections: THEMES, examples: EXAMPLES });
    });

    await test.step('Когда он открывает слово', async () => {
      await openWord(page);
    });

    await test.step('Тогда карточки идут темами и «Без темы» в конце, в каждой слово выделено', async () => {
      await expect(page.getByRole('heading', { level: 3 })).toHaveText(['Reisen / Bahn', 'Alltag', 'Без темы']);
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue(SEEDED);
      await expect(translation(card(page, 'Reisen / Bahn'))).toHaveValue('Встречаемся в восемь на вокзале.');
      for (const name of ['Reisen / Bahn', 'Alltag', 'Без темы']) {
        await expect(card(page, name).locator('strong')).toHaveText('Bahnhof');
      }
    });
  });

  test('пользователь открывает слово в теме → карточка темы первая, остальные следом', async ({ page }) => {
    await test.step('Дано слово в двух темах, активна «Alltag»', async () => {
      await seedWord(page, { active: 'collection:alltag', collections: THEMES, examples: EXAMPLES });
    });

    await test.step('Когда он открывает слово', async () => {
      await openWord(page);
    });

    await test.step('Тогда первой идёт карточка «Alltag»', async () => {
      await expect(page.getByRole('heading', { level: 3 })).toHaveText(['Alltag', 'Reisen / Bahn', 'Без темы']);
    });
  });

  test('пользователь открывает слово, лежащее только в дочерней теме активной папки → видит карточку дочерней темы', async ({ page }) => {
    await test.step('Дано папка «Reisen» активна, слово лежит только в «Reisen/Bahn»', async () => {
      await seedWord(page, {
        active: 'collection:reisen',
        collections: [{ id: 'collection:reisen', name: 'Reisen', words: [] }, { id: 'collection:bahn', name: 'Reisen/Bahn' }],
        examples: [EXAMPLES[0]],
      });
    });

    await test.step('Когда он открывает слово', async () => {
      await openWord(page);
    });

    await test.step('Тогда первой идёт карточка «Reisen / Bahn» с её примером', async () => {
      await expect(page.getByRole('heading', { level: 3 }).first()).toHaveText('Reisen / Bahn');
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue(SEEDED);
    });
  });

  test('пользователь нажимает «Другой пример» → видит другой пример (тело запроса несёт текущий)', async ({ page }) => {
    let asked;

    await test.step('Дано открытое слово, сервер отвечает другим примером', async () => {
      await seedWord(page, { collections: THEMES, examples: EXAMPLES });
      asked = await stubRegeneration(page, answer(ANOTHER, 'Вокзал находится прямо в центре.'));
      await openWord(page);
    });

    await test.step('Когда он нажимает «Другой пример» на карточке «Reisen / Bahn»', async () => {
      await card(page, 'Reisen / Bahn').getByRole('button', { name: 'Другой пример' }).click();
    });

    await test.step('Тогда на карточке другой пример, а запрос нёс слово, перевод, тему и текущее предложение', async () => {
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue(ANOTHER);
      await expect(translation(card(page, 'Reisen / Bahn'))).toHaveValue('Вокзал находится прямо в центре.');
      await expect(sentence(card(page, 'Alltag'))).toHaveValue(TRIP);
      expect(asked).toHaveLength(1);
      expect(asked[0].word).toBe('Bahnhof');
      expect(asked[0].translations).toEqual(['вокзал']);
      expect(asked[0].context).toBe('Reisen/Bahn');
      expect(asked[0].avoid).toEqual([SEEDED]);
    });
  });

  test('пользователь ждёт другой пример → видит «Подбираю пример…», кнопки не нажать, предложение бледнеет', async ({ page }) => {
    await test.step('Дано открытое слово, сервер не отвечает', async () => {
      await seedWord(page, { collections: THEMES, examples: EXAMPLES });
      await page.route('**/api/examples', () => {});
      await openWord(page);
    });

    await test.step('Когда он нажимает «Другой пример»', async () => {
      await card(page, 'Reisen / Bahn').getByRole('button', { name: 'Другой пример' }).click();
    });

    await test.step('Тогда на карточке «Подбираю пример…», а кнопки всех карточек недоступны', async () => {
      await expect(card(page, 'Reisen / Bahn').getByText('Подбираю пример…')).toBeVisible();
      await expect(card(page, 'Alltag').getByRole('button', { name: 'Другой пример' })).toBeDisabled();
      await expect(card(page, 'Reisen / Bahn').getByRole('button', { name: 'Другой пример' })).toBeDisabled();
    });
  });

  test('пользователь просит другой пример, но связи нет → видит «Не получилось», текущий пример остаётся', async ({ page }) => {
    await test.step('Дано открытое слово, связи с сервером нет', async () => {
      await seedWord(page, { collections: THEMES, examples: EXAMPLES });
      await stubRegeneration(page, (route) => route.abort('failed'));
      await openWord(page);
    });

    await test.step('Когда он нажимает «Другой пример»', async () => {
      await card(page, 'Reisen / Bahn').getByRole('button', { name: 'Другой пример' }).click();
    });

    await test.step('Тогда на карточке «Не получилось. Нажмите ↻ ещё раз.», а прежний пример на месте', async () => {
      await expect(card(page, 'Reisen / Bahn').getByText('Не получилось. Нажмите ↻ ещё раз.')).toBeVisible();
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue(SEEDED);
      await expect(card(page, 'Reisen / Bahn').getByRole('button', { name: 'Другой пример' })).toBeEnabled();
    });
  });

  test('пользователь правит предложение и перевод на карточке и сохраняет → при повторном открытии видит своё, у темы один пример', async ({ page }) => {
    await test.step('Дано открытое слово в одной теме', async () => {
      await seedWord(page, { collections: [THEMES[0]], examples: [EXAMPLES[0]] });
      await openWord(page);
    });

    await test.step('Когда он правит предложение и перевод на карточке и жмёт «Сохранить»', async () => {
      await sentence(card(page, 'Reisen / Bahn')).fill('Ich warte am Bahnhof auf dich.');
      await translation(card(page, 'Reisen / Bahn')).fill('Я жду тебя на вокзале.');
      await save(page).click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    await test.step('Тогда при повторном открытии на карточке своё, а в базе у слова один пример', async () => {
      await openWord(page);
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue('Ich warte am Bahnhof auf dich.');
      await expect(translation(card(page, 'Reisen / Bahn'))).toHaveValue('Я жду тебя на вокзале.');
      await expect.poll(async () => (await docsOfType(page, 'user-db', 'example')).length).toBe(1);
    });
  });

  test('пользователь пишет пример в пустую карточку темы и сохраняет → при повторном открытии видит его', async ({ page }) => {
    await test.step('Дано слово в теме «Reisen/Bahn», у которой нет примера', async () => {
      await seedWord(page, { collections: [THEMES[0]] });
      await openWord(page);
    });

    await test.step('Когда он пишет предложение и перевод в карточку и жмёт «Сохранить»', async () => {
      await sentence(card(page, 'Reisen / Bahn')).fill('Der Bahnhof ist groß.');
      await translation(card(page, 'Reisen / Bahn')).fill('Вокзал большой.');
      await save(page).click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    await test.step('Тогда при повторном открытии карточка с этим примером', async () => {
      await openWord(page);
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue('Der Bahnhof ist groß.');
      await expect(translation(card(page, 'Reisen / Bahn'))).toHaveValue('Вокзал большой.');
    });
  });

  test('пользователь стёр перевод примера → «Сохранить» недоступна', async ({ page }) => {
    await test.step('Дано открытое слово', async () => {
      await seedWord(page, { collections: [THEMES[0]], examples: [EXAMPLES[0]] });
      await openWord(page);
    });

    await test.step('Когда он стирает перевод примера', async () => {
      await translation(card(page, 'Reisen / Bahn')).fill('');
    });

    await test.step('Тогда «Сохранить» неактивна, а после ввода перевода снова активна', async () => {
      await expect(save(page)).toBeDisabled();
      await translation(card(page, 'Reisen / Bahn')).fill('Я жду.');
      await expect(save(page)).toBeEnabled();
    });
  });

  test('пользователь открыл слово и ничего не менял → «Сбросить» и «Сохранить» недоступны', async ({ page }) => {
    await test.step('Дано слово', async () => {
      await seedWord(page, { collections: THEMES, examples: EXAMPLES });
    });

    await test.step('Когда он открывает его', async () => {
      await openWord(page);
    });

    await test.step('Тогда обе кнопки недоступны', async () => {
      await expect(reset(page)).toBeDisabled();
      await expect(save(page)).toBeDisabled();
    });
  });

  test('пользователь правит слово, переводы и пример и жмёт «Сбросить» → всё как при открытии, окно открыто', async ({ page }) => {
    await test.step('Дано открытое слово', async () => {
      await seedWord(page, { collections: [THEMES[0]], examples: [EXAMPLES[0]] });
      await openWord(page);
    });

    await test.step('Когда он правит слово, переводы и пример и жмёт «Сбросить»', async () => {
      await page.getByLabel('Слово', { exact: true }).fill('Bahnhoff');
      await page.getByLabel('Переводы, по одному на строку').fill('станция\nвокзал');
      await sentence(card(page, 'Reisen / Bahn')).fill('Etwas anderes.');
      await expect(reset(page)).toBeEnabled();
      await reset(page).click();
    });

    await test.step('Тогда поля прежние, окно открыто, обе кнопки недоступны', async () => {
      await expect(page.getByLabel('Слово', { exact: true })).toHaveValue('Bahnhof');
      await expect(page.getByLabel('Переводы, по одному на строку')).toHaveValue('вокзал');
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue(SEEDED);
      await expect(page.getByRole('dialog')).toBeVisible();
      await expect(reset(page)).toBeDisabled();
      await expect(save(page)).toBeDisabled();
    });
  });

  test('пользователь правит пример и закрывает окно клавишей Esc → правки не сохранены', async ({ page }) => {
    await test.step('Дано открытое слово, пример исправлен', async () => {
      await seedWord(page, { collections: [THEMES[0]], examples: [EXAMPLES[0]] });
      await openWord(page);
      await sentence(card(page, 'Reisen / Bahn')).fill('Etwas anderes.');
    });

    await test.step('Когда он нажимает Esc', async () => {
      await page.keyboard.press('Escape');
      await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    await test.step('Тогда при повторном открытии прежний пример', async () => {
      await openWord(page);
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue(SEEDED);
    });
  });

  test('пользователь нажимает на фон вокруг окна → окно закрывается, правки не сохранены', async ({ page }) => {
    await test.step('Дано открытое слово, пример исправлен', async () => {
      await seedWord(page, { collections: [THEMES[0]], examples: [EXAMPLES[0]] });
      await openWord(page);
      await sentence(card(page, 'Reisen / Bahn')).fill('Etwas anderes.');
    });

    await test.step('Когда он нажимает на фон в углу страницы', async () => {
      await page.mouse.click(4, 4);
      await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    await test.step('Тогда при повторном открытии прежний пример', async () => {
      await openWord(page);
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue(SEEDED);
    });
  });

  test('пользователь запросил пример и закрыл окно → запрос прерван', async ({ page }) => {
    let aborted;

    await test.step('Дано открытое слово, сервер не отвечает', async () => {
      await seedWord(page, { collections: [THEMES[0]], examples: [EXAMPLES[0]] });
      await page.route('**/api/examples', () => {});
      aborted = page.waitForEvent('requestfailed', (request) => request.url().includes('/api/examples'));
      await openWord(page);
    });

    await test.step('Когда он нажимает «Другой пример» и закрывает окно', async () => {
      await page.getByRole('button', { name: 'Другой пример' }).click();
      await expect(page.getByText('Подбираю пример…')).toBeVisible();
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда запрос прерван', async () => {
      await aborted;
    });
  });

  test('пользователь правит пример на одной карточке и сохраняет → остальные карточки не тронуты', async ({ page }) => {
    await test.step('Дано слово в двух темах и пример без темы', async () => {
      await seedWord(page, { collections: THEMES, examples: EXAMPLES });
      await openWord(page);
    });

    await test.step('Когда он правит предложение на карточке «Alltag» и жмёт «Сохранить»', async () => {
      await sentence(card(page, 'Alltag')).fill('Neu am Bahnhof.');
      await save(page).click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    await test.step('Тогда «Alltag» своя, остальные прежние, примеров по-прежнему три', async () => {
      await openWord(page);
      await expect(sentence(card(page, 'Alltag'))).toHaveValue('Neu am Bahnhof.');
      await expect(sentence(card(page, 'Reisen / Bahn'))).toHaveValue(SEEDED);
      await expect(sentence(card(page, 'Без темы'))).toHaveValue(BARE);
      await expect.poll(async () => (await docsOfType(page, 'user-db', 'example')).length).toBe(3);
    });
  });

  test('пользователь пишет в предложение → карточки одной высоты, ничего не сдвигается', async ({ page }) => {
    let before;
    const geometry = async () => ({
      cards: await page.getByRole('region').evaluateAll((nodes) => nodes.map((n) => n.getBoundingClientRect().height)),
      head: await page.getByLabel('Слово', { exact: true }).boundingBox(),
      sentence: await sentence(card(page, 'Reisen / Bahn')).boundingBox(),
    });

    await test.step('Дано слово с примерами разной длины', async () => {
      await seedWord(page, { collections: THEMES, examples: EXAMPLES });
      await openWord(page);
      before = await geometry();
    });

    await test.step('Тогда все карточки одной высоты', async () => {
      expect(new Set(before.cards).size).toBe(1);
    });

    await test.step('Когда он нажимает в предложение и дописывает слово', async () => {
      await sentence(card(page, 'Reisen / Bahn')).click();
      await page.keyboard.press('End');
      await page.keyboard.type(' x');
    });

    await test.step('Тогда слово и предложение на тех же местах, а карточки по-прежнему одной высоты', async () => {
      const after = await geometry();
      expect(after.head).toEqual(before.head);
      expect(after.sentence.y).toBe(before.sentence.y);
      expect(new Set(after.cards).size).toBe(1);
    });
  });

  test('пользователь открывает слово в теме с длинным путём → видит заголовок карточки, сокращённый до первой и последней частей', async ({ page }) => {
    const path = 'Netflix/Babylon Berlin/Staffel 2/Folge 7';

    await test.step('Дано слово в теме с путём из четырёх частей', async () => {
      await seedWord(page, {
        collections: [{ id: 'collection:netflix', name: path }],
        examples: [{ collection: 'collection:netflix', value: SEEDED, translation: 'Встречаемся.' }],
      });
    });

    await test.step('Когда он открывает слово', async () => {
      await openWord(page);
    });

    await test.step('Тогда заголовок «Netflix / … / Folge 7»', async () => {
      await expect(page.getByRole('heading', { level: 3 })).toHaveText('Netflix / … / Folge 7');
    });
  });
});
