const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { seedExample } = require('./lesson-answer.shared');

// An example is answered with tiles (GH-573, GH-605, GH-652).

const SENTENCE = ['Der', 'Hund', 'schläft', 'im', 'Garten'];

const hint = (page) => page.getByRole('button', { name: 'Подсказка: собрать из плиток' });
const field = (page) => page.getByRole('textbox', { name: 'Ответ на немецком' });
const tray = (page) => page.getByRole('group', { name: 'Ваш ответ' });
const bank = (page) => page.getByRole('group', { name: 'Плитки' });
const marked = (page) => bank(page).locator('b');
// The bank is shuffled, so the marked letters are compared sorted.
const markedTexts = async (page) => (await marked(page).allTextContents()).sort();
const check = (page) => page.getByRole('button', { name: 'ПРОВЕРИТЬ' });

async function showWord(page, sentence) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await seedExample(page, undefined, sentence);
  await page.goto('/lesson');
  await expect(page.locator('.lesson__prompt')).toBeVisible();
}

// Answers the word, and leaves the example on screen.
async function showExample(page, sentence) {
  await showWord(page, sentence);
  await field(page).fill('der Hund');
  await check(page).click();
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(page.locator('.lesson__instruction')).toContainText('предложение');
}

async function place(page, words) {
  for (const word of words) {
    await bank(page).getByRole('button', { name: word, exact: true }).click();
  }
}

test.describe('Плитки у примера', () => {
  test('ученик переводит слово → обычное поле, без плиток и лампочки', async ({ page }) => {
    await test.step('Когда на экране слово', async () => {
      await showWord(page);
    });

    await test.step('Тогда есть обычное поле, нет плиток и лампочки', async () => {
      await expect(field(page)).toBeVisible();
      await expect(hint(page)).toBeHidden();
      await expect(bank(page).getByRole('button')).toHaveCount(0);
    });
  });

  test('ученик видит пример → вместо поля плитки всех слов, лампочки нет', async ({ page }) => {
    await test.step('Когда на экране пример', async () => {
      await showExample(page);
    });

    await test.step('Тогда поля для ввода нет, плитки всех слов без знаков препинания, лампочки нет', async () => {
      await expect(field(page)).toHaveCount(0);
      await expect(hint(page)).toHaveCount(0);
      await expect(tray(page).getByRole('button')).toHaveCount(0);
      const texts = await bank(page).getByRole('button').allTextContents();
      expect([...texts].sort()).toEqual([...SENTENCE].sort());
    });
  });

  test('ученик нажал поставленную плитку → она вернулась в банк', async ({ page }) => {
    await test.step('Дано две поставленные плитки', async () => {
      await showExample(page);
      await place(page, ['Der', 'Hund']);
      await expect(tray(page).getByRole('button')).toHaveText(['Der', 'Hund']);
    });

    await test.step('Когда он нажимает «Der» в лотке', async () => {
      await tray(page).getByRole('button', { name: 'Der', exact: true }).click();
    });

    await test.step('Тогда «Der» снова в банке, в лотке одна плитка', async () => {
      await expect(tray(page).getByRole('button')).toHaveText(['Hund']);
      await expect(bank(page).getByRole('button', { name: 'Der', exact: true })).toBeVisible();
      await expect(bank(page).getByRole('button')).toHaveCount(4);
    });
  });

  test('ученик перетащил плитку → порядок в ответе изменился', async ({ page }) => {
    await test.step('Дано расставленные плитки', async () => {
      await showExample(page);
      await place(page, SENTENCE);
    });

    await test.step('Когда он перетаскивает «Der» правее «schläft»', async () => {
      const from = await tray(page).getByRole('button', { name: 'Der', exact: true }).boundingBox();
      const to = await tray(page).getByRole('button', { name: 'schläft', exact: true }).boundingBox();
      await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
      await page.mouse.down();
      await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 8 });
      await page.mouse.move(to.x + to.width - 4, to.y + to.height / 2, { steps: 4 });
      await page.mouse.up();
    });

    await test.step('Тогда «Der» стоит после «schläft»', async () => {
      await expect(tray(page).getByRole('button')).toHaveText(['Hund', 'schläft', 'Der', 'im', 'Garten']);
    });
  });

  test('ученик проверил неполный ответ из плиток → ответ засчитан как неверный', async ({ page }) => {
    await test.step('Дано расставлены не все плитки', async () => {
      await showExample(page);
      await place(page, SENTENCE.slice(0, 4));
      await expect(check(page)).toBeEnabled();
    });

    await test.step('Когда он нажимает ПРОВЕРИТЬ', async () => {
      await check(page).click();
    });

    await test.step('Тогда он видит «Правильно:», свой неполный ответ и эталон', async () => {
      await expect(page.getByRole('heading', { name: 'Правильно:' })).toBeVisible();
      await expect(page.getByText('Der Hund schläft im', { exact: true })).toBeVisible();
    });
  });

  test('ученик расставил плитки и проверил → Правильно!', async ({ page }) => {
    await test.step('Дано плитки в порядке предложения', async () => {
      await showExample(page);
      await place(page, SENTENCE);
    });

    await test.step('Когда он нажимает ПРОВЕРИТЬ', async () => {
      await check(page).click();
    });

    await test.step('Тогда он видит «Правильно!»', async () => {
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    });
  });

  async function dragTo(page, tile, x, y) {
    const from = await tile.boundingBox();
    await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
    await page.mouse.down();
    await page.mouse.move(x, y, { steps: 10 });
  }

  test('ученик перетащил плитку из банка в лоток → она встала в место каретки', async ({ page }) => {
    await test.step('Дано в лотке «Der» и «Hund»', async () => {
      await showExample(page);
      await place(page, ['Der', 'Hund']);
    });

    await test.step('Когда он перетаскивает «im» между ними и отпускает', async () => {
      const first = await tray(page).getByRole('button', { name: 'Der', exact: true }).boundingBox();
      await dragTo(page, bank(page).getByRole('button', { name: 'im', exact: true }),
        first.x + first.width - 2, first.y + first.height / 2);
      await page.mouse.up();
    });

    await test.step('Тогда «im» в лотке между «Der» и «Hund», в банке три плитки', async () => {
      await expect(tray(page).getByRole('button')).toHaveText(['Der', 'im', 'Hund']);
      await expect(bank(page).getByRole('button')).toHaveCount(2);
    });
  });

  test('ученик отпустил плитку из банка вне лотка → она осталась в банке', async ({ page }) => {
    await test.step('Дано плитки в банке', async () => {
      await showExample(page);
    });

    await test.step('Когда он тянет плитку на банк и отпускает вне лотка', async () => {
      const other = await bank(page).getByRole('button', { name: 'Garten', exact: true }).boundingBox();
      await dragTo(page, bank(page).getByRole('button', { name: 'im', exact: true }),
        other.x + other.width / 2, other.y - 20);
      await page.mouse.up();
    });

    await test.step('Тогда лоток пуст, в банке пять плиток', async () => {
      await expect(tray(page).getByRole('button')).toHaveCount(0);
      await expect(bank(page).getByRole('button')).toHaveCount(5);
    });
  });

  test('ученик перетащил плитку из банка в пустой лоток → она в лотке', async ({ page }) => {
    await test.step('Дано пустой лоток', async () => {
      await showExample(page);
    });

    await test.step('Когда он тянет «Hund» в лоток и отпускает', async () => {
      const box = await page.locator('#lesson-answer #field').boundingBox();
      await dragTo(page, bank(page).getByRole('button', { name: 'Hund', exact: true }),
        box.x + box.width / 2, box.y + box.height / 2);
      await page.mouse.up();
    });

    await test.step('Тогда «Hund» в лотке', async () => {
      await expect(tray(page).getByRole('button')).toHaveText(['Hund']);
    });
  });

  test('ученик перетащил плитку и набирает слово → плитка встаёт в лоток без щелчка по полю', async ({ page }) => {
    await test.step('Дано «Hund» перетащена в лоток', async () => {
      await showExample(page);
      const box = await page.locator('#lesson-answer #field').boundingBox();
      await dragTo(page, bank(page).getByRole('button', { name: 'Hund', exact: true }),
        box.x + box.width / 2, box.y + box.height / 2);
      await page.mouse.up();
      await expect(tray(page).getByRole('button')).toHaveText(['Hund']);
    });

    await test.step('Когда он набирает «der»', async () => {
      await page.keyboard.type('der');
    });

    await test.step('Тогда «Der» в лотке после «Hund»', async () => {
      await expect(tray(page).getByRole('button')).toHaveText(['Hund', 'Der']);
    });
  });

  test('ученик набирает начало слова на клавиатуре → плитка встаёт в конец лотка', async ({ page }) => {
    await test.step('Дано плитки в банке', async () => {
      await showExample(page);
    });

    await test.step('Когда он набирает «h», затем «DER» заглавными', async () => {
      await page.keyboard.type('h');
      await page.keyboard.type('DER');
    });

    await test.step('Тогда «Hund» и «Der» в лотке в этом порядке и ничего не выделено', async () => {
      await expect(tray(page).getByRole('button')).toHaveText(['Hund', 'Der']);
      await expect(marked(page)).toHaveCount(0);
    });
  });

  test('ученик набирает слова с умляутом и ß без них → ставятся «über» и «groß»', async ({ page }) => {
    await test.step('Дано плитки «über», «Uhr», «große» и «groß» в банке', async () => {
      await showExample(page, 'Der große Hund ist über der Uhr groß.');
    });

    await test.step('Когда он набирает «ub»', async () => {
      await page.keyboard.type('ub');
    });

    await test.step('Тогда «über» в лотке', async () => {
      await expect(tray(page).getByRole('button')).toHaveText(['über']);
    });

    await test.step('Когда он набирает «gross»', async () => {
      await page.keyboard.type('gross');
    });

    await test.step('Тогда «groß» выделено целиком на обеих плитках', async () => {
      await expect.poll(() => markedTexts(page)).toEqual(['groß', 'groß']);    });

    await test.step('Когда он жмёт Space', async () => {
      await page.keyboard.press('Space');
    });

    await test.step('Тогда «groß» в лотке после «über»', async () => {
      await expect(tray(page).getByRole('button')).toHaveText(['über', 'groß']);
    });
  });

  test('ученик набирает букву, с которой не начинается ни одно слово → буква не принята', async ({ page }) => {
    await test.step('Дано набрано «d» в банке с «Die» и «dieser»', async () => {
      await showExample(page, 'Die Katze liebt dieser Garten.');
      await page.keyboard.type('d');
      await expect.poll(() => markedTexts(page)).toEqual(['D', 'd']);
    });

    await test.step('Когда он набирает «u»', async () => {
      await page.keyboard.type('u');
    });

    await test.step('Тогда ничего не поставлено, выделена по-прежнему только «d»', async () => {
      await expect(tray(page).getByRole('button')).toHaveCount(0);
      await expect.poll(() => markedTexts(page)).toEqual(['D', 'd']);
    });
  });

  test('ученик набирает начало слов → буквы жирнее, плитки не меняют ширину', async ({ page }) => {
    const widths = () => bank(page).getByRole('button').evaluateAll((tiles) => tiles.map((tile) => tile.getBoundingClientRect().width));

    await test.step('Дано плитки в банке', async () => {
      await showExample(page, 'Die Katze liebt dieser Garten.');
    });
    const before = await widths();

    await test.step('Когда он набирает «di»', async () => {
      await page.keyboard.type('di');
    });

    await test.step('Тогда «Di» и «di» жирнее остального текста плитки, ширины те же', async () => {
      await expect.poll(() => markedTexts(page)).toEqual(['Di', 'di']);
      const [tileWeight, markedWeight] = await marked(page).first().evaluate((el) => [
        getComputedStyle(el.closest('button')).fontWeight,
        getComputedStyle(el).fontWeight,
      ]);
      expect(Number(markedWeight)).toBeGreaterThan(Number(tileWeight));
      const during = await widths();
      during.forEach((width, i) => expect(Math.abs(width - before[i]), `плитка ${i}`).toBeLessThanOrEqual(0.5));
    });

    await test.step('Когда он стирает префикс', async () => {
      await page.keyboard.press('Backspace');
      await page.keyboard.press('Backspace');
    });

    await test.step('Тогда выделения нет, ширины те же', async () => {
      await expect(marked(page)).toHaveCount(0);
      const after = await widths();
      after.forEach((width, i) => expect(Math.abs(width - before[i]), `плитка ${i}`).toBeLessThanOrEqual(0.5));
    });
  });

  for (const key of ['Space', 'Enter']) {
    test(`ученик набирает слово, являющееся началом другого, и жмёт ${key} → ставится точное слово`, async ({ page }) => {
      await test.step('Дано плитки «Die» и «dieser» в банке', async () => {
        await showExample(page, 'Die Katze liebt dieser Garten.');
      });

      await test.step('Когда он набирает «die»', async () => {
        await page.keyboard.type('die');
      });

      await test.step('Тогда «Die» и «die» выделены, плитка не поставлена', async () => {
        await expect.poll(() => markedTexts(page)).toEqual(['Die', 'die']);
        await expect(tray(page).getByRole('button')).toHaveCount(0);
      });

      await test.step(`Когда он жмёт ${key}`, async () => {
        await page.keyboard.press(key);
      });

      await test.step('Тогда «Die» в лотке, выделения нет', async () => {
        await expect(tray(page).getByRole('button')).toHaveText(['Die']);
        await expect(marked(page)).toHaveCount(0);
      });
    });
  }

  test('ученик набирает начало, не являющееся целым словом, и жмёт Пробел и Enter → ничего не ставится', async ({ page }) => {
    await test.step('Дано набрано «di» в банке с «Die» и «dieser»', async () => {
      await showExample(page, 'Die Katze liebt dieser Garten.');
      await page.keyboard.type('di');
      await expect.poll(() => markedTexts(page)).toEqual(['Di', 'di']);
    });

    await test.step('Когда он жмёт Пробел и Enter', async () => {
      await page.keyboard.press('Space');
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда лоток пуст, «di» всё ещё выделено', async () => {
      await expect(tray(page).getByRole('button')).toHaveCount(0);
      await expect.poll(() => markedTexts(page)).toEqual(['Di', 'di']);
    });
  });

  test('ученик набирает слово, которое есть в банке дважды → уходит первая по порядку в банке плитка', async ({ page }) => {
    let order;

    await test.step('Дано плитки «Der» и «der» в банке', async () => {
      await showExample(page, 'Der Hund sieht der Katze.');
      order = await bank(page).getByRole('button').allTextContents();
    });

    await test.step('Когда он набирает «der»', async () => {
      await page.keyboard.type('der');
    });

    await test.step('Тогда в лотке первая из двух по порядку в банке, другая осталась на месте', async () => {
      const first = order.findIndex((word) => word.toLowerCase() === 'der');
      await expect(tray(page).getByRole('button')).toHaveText([order[first]]);
      await expect(bank(page).getByRole('button')).toHaveText(order.filter((word, i) => i !== first));
    });
  });

  test('ученик жмёт Backspace → стирается буква, а без префикса плитка возвращается в банк', async ({ page }) => {
    await test.step('Дано поставлена «Katze» и набрано «die»', async () => {
      await showExample(page, 'Die Katze liebt dieser Garten.');
      await page.keyboard.type('ka');
      await page.keyboard.type('die');
      await expect.poll(() => markedTexts(page)).toEqual(['Die', 'die']);
    });

    await test.step('Когда он жмёт Backspace', async () => {
      await page.keyboard.press('Backspace');
    });

    await test.step('Тогда выделено «Di» и «di»', async () => {
      await expect.poll(() => markedTexts(page)).toEqual(['Di', 'di']);
    });

    await test.step('Когда он стирает префикс и жмёт Backspace ещё раз', async () => {
      await page.keyboard.press('Backspace');
      await page.keyboard.press('Backspace');
      await page.keyboard.press('Backspace');
    });

    await test.step('Тогда «Katze» вернулась в банк', async () => {
      await expect(tray(page).getByRole('button')).toHaveCount(0);
      await expect(bank(page).getByRole('button')).toHaveCount(5);
    });
  });
});
