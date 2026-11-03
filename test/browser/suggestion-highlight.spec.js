const { test, expect } = require('./fixtures');
const { openHome } = require('./service-worker.shared');

// GH-412: the arrows moved the active index in state and Enter picked by it,
// but no row on screen was ever marked. These assertions are on the mark
// itself, which is the only thing the eye has to go on.
//
// `fe` matches four fixture lemmas (README.md), so there is room to move down
// twice and back up once.

const options = (page) => page.getByRole('option');

// The value field's label follows the mode, so the German field is addressed
// by the label it carries while the value is a single word.
const valueField = (page) => page.getByLabel('Слово (немецкий)');

const expectOnlyActive = async (page, index, total) => {
  for (let i = 0; i < total; i += 1) {
    if (i === index) {
      await expect(options(page).nth(i)).toHaveAttribute('data-active', '');
    } else {
      await expect(options(page).nth(i)).not.toHaveAttribute('data-active', '');
    }
  }
};

test.describe('Подсказки слов при вводе', () => {
  test('пользователь жмёт стрелки → отмеченная подсказка перемещается по списку', async ({ page }) => {
    const field = valueField(page);

    await test.step('Дано четыре подсказки на «fe»', async () => {
      await openHome(page);
      await expect(field).toBeVisible();
      // Four suggestions are the signal that the fixture is being served.
      await field.pressSequentially('fe');
      await expect(options(page)).toHaveCount(4);
    });

    await test.step('Тогда отмечена первая — её выберет Enter без стрелок', async () => {
      await expectOnlyActive(page, 0, 4);
    });

    await test.step('Когда он жмёт ↓ дважды и ↑ один раз', async () => {
      await page.keyboard.press('ArrowDown');
      await expectOnlyActive(page, 1, 4);
      await page.keyboard.press('ArrowDown');
      await expectOnlyActive(page, 2, 4);
      await page.keyboard.press('ArrowUp');
    });

    await test.step('Тогда отмечена вторая', async () => {
      await expectOnlyActive(page, 1, 4);
    });
  });

  test('пользователь жмёт стрелки у краёв списка → отметка не выходит за список', async ({ page }) => {
    await test.step('Дано четыре подсказки на «fe»', async () => {
      await openHome(page);
      await valueField(page).pressSequentially('fe');
      await expect(options(page)).toHaveCount(4);
    });

    await test.step('Когда он жмёт ↑ на первой', async () => {
      await page.keyboard.press('ArrowUp');
    });

    await test.step('Тогда отмечена всё та же первая', async () => {
      await expectOnlyActive(page, 0, 4);
    });

    await test.step('Когда он жмёт ↓ шесть раз', async () => {
      for (let i = 0; i < 6; i += 1) await page.keyboard.press('ArrowDown');
    });

    await test.step('Тогда отмечена последняя', async () => {
      await expectOnlyActive(page, 3, 4);
    });
  });

  test('пользователь отметил подсказку стрелкой и жмёт Enter → в поле слова именно она', async ({ page }) => {
    const field = valueField(page);
    let marked;

    await test.step('Дано четыре подсказки на «fe»', async () => {
      await openHome(page);
      await field.pressSequentially('fe');
      await expect(options(page)).toHaveCount(4);
    });

    await test.step('Когда он отмечает вторую стрелкой ↓', async () => {
      await page.keyboard.press('ArrowDown');
      marked = (await options(page).nth(1).innerText()).trim();
    });

    await test.step('Когда он жмёт Enter', async () => {
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда в поле слова отмеченная подсказка', async () => {
      await expect(field).toHaveValue(marked);
    });
  });

  // GH-711: `sondern`, found through `gesondert`, was marked on its own, and Enter
  // meant only to move on replaced the typed word. The fixture's `Häuser` is a form
  // of `das Haus`, the same shape.
  for (const key of ['Enter', 'Tab']) {
    test(`пользователь печатает словоформу и жмёт ${key} → в поле остаётся словоформа, перевод пуст`, async ({ page }) => {
      const field = valueField(page);
      const translation = page.getByLabel('Перевод (русский)');

      await test.step('Дано подсказка «das Haus» на «Häuser»', async () => {
        await openHome(page);
        await field.pressSequentially('Häuser');
        await expect(options(page)).toHaveText(['das Haus']);
      });

      await test.step('Тогда подсказка не отмечена, перевод не подставлен', async () => {
        await expect(options(page).first()).not.toHaveAttribute('data-active', '');
        await expect(translation).toHaveValue('');
      });

      await test.step(`Когда он жмёт ${key}`, async () => {
        await page.keyboard.press(key);
      });

      await test.step('Тогда в поле слова «Häuser», перевод пуст, фокус в переводе', async () => {
        await expect(field).toHaveValue('Häuser');
        await expect(translation).toHaveValue('');
        await expect(translation).toBeFocused();
      });
    });
  }

  // The form stores one translation a line, so the dictionary's several translations come one a line too.
  test('пользователь печатает слово с двумя переводами в словаре → в поле перевода каждый на своей строке', async ({ page }) => {
    const field = valueField(page);
    const translation = page.getByLabel('Перевод (русский)');

    await test.step('Дано подсказка «die Katze» на «Katz»', async () => {
      await openHome(page);
      await field.pressSequentially('Katz');
      await expect(options(page)).toHaveText(['die Katze']);
    });

    await test.step('Тогда в поле перевода «кошка» и «кот» на разных строках', async () => {
      await expect(translation).toHaveValue('кошка\nкот');
    });

    await test.step('Когда он выбирает подсказку Enter', async () => {
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда переводы всё так же на разных строках', async () => {
      await expect(translation).toHaveValue('кошка\nкот');
    });
  });

  // GH-719: the prefill was read off the open list, so closing the list emptied the field.
  test('пользователь закрывает подсказки Escape или уходит в поле перевода → перевод из словаря остаётся', async ({ page }) => {
    const field = valueField(page);
    const translation = page.getByLabel('Перевод (русский)');

    await test.step('Дано подсказка «die Katze» на «Katz» и перевод из словаря', async () => {
      await openHome(page);
      await field.pressSequentially('Katz');
      await expect(options(page)).toHaveText(['die Katze']);
      await expect(translation).toHaveValue('кошка\nкот');
    });

    await test.step('Когда он жмёт Escape', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда подсказок нет, а перевод на месте', async () => {
      await expect(options(page)).toHaveCount(0);
      await expect(translation).toHaveValue('кошка\nкот');
    });

    // The open list covers the translation field, so focus moves there as the phone keyboard's «next» does.
    await test.step('Когда он печатает дальше и переходит в поле перевода', async () => {
      await field.pressSequentially('e');
      await expect(options(page)).toHaveText(['die Katze']);
      await translation.focus();
    });

    await test.step('Тогда подсказок нет, а перевод на месте', async () => {
      await expect(options(page)).toHaveCount(0);
      await expect(translation).toHaveValue('кошка\nкот');
    });
  });

  test('пользователь печатает слово и сразу жмёт «Добавить» → слово добавлено с переводом из словаря', async ({ page }) => {
    const field = valueField(page);

    await test.step('Дано подсказка «die Katze» на «Katz» и перевод из словаря', async () => {
      await openHome(page);
      await field.pressSequentially('Katz');
      await expect(page.getByLabel('Перевод (русский)')).toHaveValue('кошка\nкот');
    });

    await test.step('Когда он жмёт «Добавить»', async () => {
      await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
    });

    await test.step('Тогда форма очищена: слово сохранено', async () => {
      await expect(field).toHaveValue('');
      await expect(page.getByRole('alert')).toHaveCount(0);
    });
  });

  test('пользователь печатает словоформу, жмёт ↓ и Enter → в поле лемма с переводом', async ({ page }) => {
    const field = valueField(page);

    await test.step('Дано подсказка «das Haus» на «Häuser»', async () => {
      await openHome(page);
      await field.pressSequentially('Häuser');
      await expect(options(page)).toHaveText(['das Haus']);
    });

    await test.step('Когда он жмёт ↓ и Enter', async () => {
      await page.keyboard.press('ArrowDown');
      await expect(options(page).first()).toHaveAttribute('data-active', '');
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда в поле «das Haus», в переводе «дом»', async () => {
      await expect(field).toHaveValue('das Haus');
      await expect(page.getByLabel('Перевод (русский)')).toHaveValue('дом');
    });
  });
});
