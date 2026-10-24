const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { seedExample } = require('./lesson-answer.shared');

// The tile hint on an example (GH-573, GH-605).
// and its example, so after a wrong answer the example is the only trial left
// and comes straight back.

const SENTENCE = ['Der', 'Hund', 'schläft', 'im', 'Garten'];

const hint = (page) => page.getByRole('button', { name: 'Подсказка: собрать из плиток' });
const tray = (page) => page.getByRole('group', { name: 'Ваш ответ' });
const bank = (page) => page.getByRole('group', { name: 'Плитки' });
const marked = (page) => bank(page).locator('b');
// The bank is shuffled, so the marked letters are compared sorted.
const markedTexts = async (page) => (await marked(page).allTextContents()).sort();
const check = (page) => page.getByRole('button', { name: 'ПРОВЕРИТЬ' });

// Answers the word, and leaves the example on screen for the first time.
async function showExampleFirstTime(page, sentence) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await seedExample(page, undefined, sentence);
  await page.goto('/lesson');
  await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('der Hund');
  await check(page).click();
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(page.locator('.lesson__instruction')).toContainText('предложение');
}

async function showExampleAgain(page, sentence) {
  await showExampleFirstTime(page, sentence);
  await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('Die Katze schläft.');
  await check(page).click();
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(page.locator('#lesson-answer')).toBeVisible();
}

async function openTiles(page) {
  await hint(page).click();
  await expect(bank(page)).toBeVisible();
}

async function place(page, words) {
  for (const word of words) {
    await bank(page).getByRole('button', { name: word, exact: true }).click();
  }
}

test.describe('Подсказка плитками у повторного примера', () => {
  test('ученик видит пример впервые → есть обычное поле и лампочка', async ({ page }) => {
    await test.step('Когда пример показан впервые', async () => {
      await showExampleFirstTime(page);
    });

    await test.step('Тогда есть обычное поле, лампочка и нет плиток', async () => {
      await expect(page.locator('#lesson-answer')).toBeVisible();
      await expect(hint(page)).toBeVisible();
      await expect(bank(page).getByRole('button')).toHaveCount(0);
    });

    await test.step('Когда он нажимает лампочку', async () => {
      await openTiles(page);
    });

    await test.step('Тогда плитки на месте поля', async () => {
      await expect(bank(page).getByRole('button')).toHaveCount(5);
    });
  });

  test('ученик ошибся в примере → при повторном показе в поле есть лампочка', async ({ page }) => {
    await test.step('Когда пример показан снова после ошибки', async () => {
      await showExampleAgain(page);
    });

    await test.step('Тогда в поле есть лампочка', async () => {
      await expect(page.locator('#lesson-answer')).toBeVisible();
      await expect(hint(page)).toBeVisible();
    });
  });

  test('ученик видит пример повторно → поле ввода в фокусе, можно печатать', async ({ page }) => {
    await test.step('Когда пример показан снова после ошибки', async () => {
      await showExampleAgain(page);
    });

    await test.step('Тогда фокус в поле ввода, а не на лампочке, и набранный текст попадает в поле', async () => {
      const field = page.getByRole('textbox', { name: 'Ответ на немецком' });
      await expect(field).toBeFocused();
      await page.keyboard.type('Der Hund');
      await expect(field).toHaveValue('Der Hund');
    });
  });

  test('ученик нажал лампочку → поле заполняется плитками, лампочки нет', async ({ page }) => {
    await test.step('Дано повторный показ примера', async () => {
      await showExampleAgain(page);
    });

    await test.step('Когда он нажимает лампочку', async () => {
      await openTiles(page);
    });

    await test.step('Тогда поля для ввода нет, плитки всех слов без знаков препинания, лампочки нет', async () => {
      await expect(page.getByRole('textbox', { name: 'Ответ на немецком' })).toHaveCount(0);
      await expect(hint(page)).toHaveCount(0);
      await expect(tray(page).getByRole('button')).toHaveCount(0);
      const texts = await bank(page).getByRole('button').allTextContents();
      expect([...texts].sort()).toEqual([...SENTENCE.slice(0, 4), 'Garten'].sort());
    });
  });

  test('ученик печатает ответ и жмёт Alt+Enter → поле заполняется плитками, лампочки нет', async ({ page }) => {
    await test.step('Дано пример и набранное начало ответа', async () => {
      await showExampleFirstTime(page);
      await page.keyboard.type('Der');
    });

    await test.step('Когда он жмёт Alt+Enter', async () => {
      await page.keyboard.press('Alt+Enter');
    });

    await test.step('Тогда вместо поля плитки всех слов, лампочки нет', async () => {
      await expect(page.getByRole('textbox', { name: 'Ответ на немецком' })).toHaveCount(0);
      await expect(hint(page)).toHaveCount(0);
      await expect(bank(page).getByRole('button')).toHaveCount(5);
    });
  });

  test('ученик переходит на лампочку с клавиатуры → вокруг неё рамка фокуса', async ({ page }) => {
    // The ring is drawn inside the bulb's box, so the box alone shows it.
    const bulb = async () => page.screenshot({ clip: await hint(page).boundingBox() });

    await test.step('Дано пример, фокус в поле ответа', async () => {
      await showExampleFirstTime(page);
      await expect(page.getByRole('textbox', { name: 'Ответ на немецком' })).toBeFocused();
    });
    const unfocused = await bulb();

    await test.step('Когда он жмёт Shift+Tab', async () => {
      await page.keyboard.press('Shift+Tab');
    });

    await test.step('Тогда лампочка в фокусе и выглядит иначе — с рамкой', async () => {
      await expect(hint(page)).toBeFocused();
      expect((await bulb()).equals(unfocused)).toBe(false);
    });
  });

  for (const [label, size] of [['390x844', { width: 390, height: 844 }], ['1280x720', { width: 1280, height: 720 }]]) {
    test(`ученик нажал лампочку → поле ответа осталось на месте (${label})`, async ({ page }) => {
      const box = async () => {
        const rect = await page.locator('#lesson-answer #field').boundingBox();
        const button = await check(page).boundingBox();
        return { box: rect, button };
      };

      await test.step('Дано повторный показ примера и поле ответа', async () => {
        await page.setViewportSize(size);
        await showExampleAgain(page);
      });
      const before = await box();

      await test.step('Когда он нажимает лампочку', async () => {
        await openTiles(page);
      });

      await test.step('Тогда поле ответа и кнопка ПРОВЕРИТЬ на тех же местах, тех же размеров', async () => {
        const after = await box();
        for (const key of ['x', 'y', 'width', 'height']) {
          expect(Math.abs(after.box[key] - before.box[key]), `поле: ${key}`).toBeLessThanOrEqual(0.5);
          expect(Math.abs(after.button[key] - before.button[key]), `кнопка: ${key}`).toBeLessThanOrEqual(0.5);
        }
      });
    });
  }

  test('ученик нажал поставленную плитку → она вернулась в банк', async ({ page }) => {
    await test.step('Дано две поставленные плитки', async () => {
      await showExampleAgain(page);
      await openTiles(page);
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
      await showExampleAgain(page);
      await openTiles(page);
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
      await showExampleAgain(page);
      await openTiles(page);
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
      await showExampleAgain(page);
      await openTiles(page);
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
      await showExampleAgain(page);
      await openTiles(page);
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
      await showExampleAgain(page);
      await openTiles(page);
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
      await showExampleAgain(page);
      await openTiles(page);
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

  test('ученик набирает начало слова на клавиатуре → плитка встаёт в конец лотка', async ({ page }) => {
    await test.step('Дано плитки в банке', async () => {
      await showExampleAgain(page);
      await openTiles(page);
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

  test('ученик набирает букву, с которой не начинается ни одно слово → буква не принята', async ({ page }) => {
    await test.step('Дано набрано «d» в банке с «Die» и «dieser»', async () => {
      await showExampleAgain(page, 'Die Katze liebt dieser Garten.');
      await openTiles(page);
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
      await showExampleAgain(page, 'Die Katze liebt dieser Garten.');
      await openTiles(page);
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
        await showExampleAgain(page, 'Die Katze liebt dieser Garten.');
        await openTiles(page);
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
      await showExampleAgain(page, 'Die Katze liebt dieser Garten.');
      await openTiles(page);
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
      await showExampleAgain(page, 'Der Hund sieht der Katze.');
      await openTiles(page);
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
      await showExampleAgain(page, 'Die Katze liebt dieser Garten.');
      await openTiles(page);
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
