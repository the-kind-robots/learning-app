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
});
