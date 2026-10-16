const { test, expect } = require('./fixtures');
const { openHome } = require('./service-worker.shared');

// Suggestions kept across keystrokes stay on screen, inert where the typed text has left them behind.
// `der Rücken` and `das Rückenmark` are the only fixture lemmas under `rück`.

const options = (page) => page.getByRole('option');

// The value field's label follows the mode, so it is addressed by id.
const valueField = (page) => page.locator('#new-word-value');

const translationField = (page) => page.getByLabel('Перевод (русский)');

const inertRows = (page) => page.locator('[role="option"][aria-disabled="true"]');

// Types one keystroke and returns the rows visible in the same task, before any dictionary answer.
const rowsRightAfterTyping = (field, text) => field.evaluate((node, typed) => {
  node.value += typed;
  node.dispatchEvent(new Event('input', { bubbles: true }));
  return Array.from(document.querySelectorAll('[role="option"]'), (row) => row.innerText.trim());
}, text);

test.describe('Подсказки слов при вводе', () => {
  test('пользователь печатает дальше слова из подсказки → устаревшая подсказка остаётся, но недоступна, и нажать её нельзя', async ({ page }) => {
    const field = valueField(page);
    let lastRow;
    let rowsAfterKeystroke;

    await test.step('Дано подсказки «der Rücken» и «das Rückenmark» на «Rücken» и перевод «спина» в поле', async () => {
      await openHome(page);
      await field.pressSequentially('Rücken');
      await expect(options(page)).toHaveText(['der Rücken', 'das Rückenmark']);
      await expect(translationField(page)).toHaveValue('спина');
      lastRow = await options(page).first().boundingBox();
    });

    await test.step('Когда он печатает «k»', async () => {
      rowsAfterKeystroke = await rowsRightAfterTyping(field, 'k');
    });

    await test.step('Тогда обе подсказки на месте в задаче нажатия, но недоступны, пока словарь не ответил', async () => {
      expect(rowsAfterKeystroke).toEqual(['der Rücken', 'das Rückenmark']);
      await expect(inertRows(page)).toHaveCount(2);
    });

    await test.step('Когда он допечатывает «urse» по 60 мс и нажимает туда, где была подсказка', async () => {
      await field.pressSequentially('urse', { delay: 60 });
      await page.mouse.click(lastRow.x + lastRow.width / 2, lastRow.y + lastRow.height / 2);
    });

    await test.step('Тогда в поле слова «Rückenkurse», а перевод не «спина»', async () => {
      await expect(field).toHaveValue('Rückenkurse');
      await expect(translationField(page)).not.toHaveValue('спина');
    });
  });

  test('пользователь печатает словоформу, а не лемму → подсказка леммы остаётся, без мигания', async ({ page }) => {
    const field = valueField(page);
    let rowsAfterKeystroke;

    await test.step('Дано подсказка «das Haus» на «Häu» — по форме «Häuser»', async () => {
      await openHome(page);
      await field.pressSequentially('Häu');
      await expect(options(page)).toHaveText(['das Haus']);
    });

    await test.step('Когда он печатает «s»', async () => {
      rowsAfterKeystroke = await rowsRightAfterTyping(field, 's');
    });

    await test.step('Тогда «das Haus» остаётся и в задаче нажатия, и после ответа словаря', async () => {
      expect(rowsAfterKeystroke).toEqual(['das Haus']);
      await expect(options(page)).toHaveText(['das Haus']);
    });
  });

  test('пользователь печатает букву, с которой часть подсказок продолжается → все остаются, лишние недоступны, без мигания', async ({ page }) => {
    const field = valueField(page);
    let rowsAfterKeystroke;

    await test.step('Дано четыре подсказки на «Fe»', async () => {
      await openHome(page);
      await field.pressSequentially('Fe');
      await expect(options(page)).toHaveCount(4);
    });

    await test.step('Когда он печатает «n»', async () => {
      rowsAfterKeystroke = await rowsRightAfterTyping(field, 'n');
    });

    await test.step('Тогда в задаче нажатия все четыре на месте, а ответ словаря оставляет две на «Fen»', async () => {
      expect(rowsAfterKeystroke).toHaveLength(4);
      await expect(options(page)).toHaveText(['das Fenster', 'die Fensterbank']);
    });
  });

  test('пользователь печатает дальше, когда подсказок десять → список не сжимается и не пустеет ни на одной букве', async ({ page }) => {
    const field = valueField(page);
    const counts = [];

    await test.step('Дано десять подсказок на «Zimmer»', async () => {
      await openHome(page);
      await field.pressSequentially('Zimmer');
      await expect(options(page)).toHaveCount(10);
    });

    await test.step('Когда он печатает «tempe» по букве, не ожидая ответов', async () => {
      for (const letter of 'tempe') {
        counts.push((await rowsRightAfterTyping(field, letter)).length);
      }
    });

    await test.step('Тогда после каждой буквы на экране по-прежнему десять подсказок', async () => {
      expect(counts).toEqual([10, 10, 10, 10, 10]);
    });

    await test.step('Когда словарь отвечает, Тогда остаётся одна «die Zimmertemperatur»', async () => {
      await expect(options(page)).toHaveText(['die Zimmertemperatur']);
    });
  });
});
