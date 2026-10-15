const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { seedExample } = require('./lesson-answer.shared');

// The tile hint on an example shown again (GH-573). The lesson holds one word
// and its example, so after a wrong answer the example is the only trial left
// and comes straight back.

const SENTENCE = ['Der', 'Hund', 'schläft', 'im', 'Garten'];

const hint = (page) => page.getByRole('checkbox', { name: 'Подсказка: собрать из плиток' });
const tray = (page) => page.getByRole('group', { name: 'Ваш ответ' });
const bank = (page) => page.getByRole('group', { name: 'Плитки' });
const check = (page) => page.getByRole('button', { name: 'ПРОВЕРИТЬ' });

// Answers the word, and leaves the example on screen for the first time.
async function showExampleFirstTime(page) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await seedExample(page);
  await page.goto('/lesson');
  await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('der Hund');
  await check(page).click();
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(page.locator('.lesson__instruction')).toContainText('предложение');
}

async function showExampleAgain(page) {
  await showExampleFirstTime(page);
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
  test('ученик видит пример впервые → лампочки нет', async ({ page }) => {
    await test.step('Когда пример показан впервые', async () => {
      await showExampleFirstTime(page);
    });

    await test.step('Тогда есть обычное поле и нет лампочки', async () => {
      await expect(page.locator('#lesson-answer')).toBeVisible();
      await expect(hint(page)).toHaveCount(0);
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
});
