const { test, expect } = require('./fixtures');
const { openHome } = require('./service-worker.shared');

// GH-357: a word typed in full used to compete with its own compounds on
// rank alone, and lost — der Rücken came tenth, behind Rückenmark. In the
// fixture der Rücken (rank 100) is outranked by das Rückenmark (rank 950), so
// rank alone would put the compound first; the exact match has to win on its
// own account.

const options = (page) => page.getByRole('option');
const valueField = (page) => page.getByLabel('Слово (немецкий)');
const translationField = (page) => page.getByLabel('Перевод (русский)');

test.describe('Порядок подсказок', () => {
  test('пользователь печатает слово целиком → оно первое в подсказках, хотя составное встречается чаще', async ({ page }) => {
    await test.step('Дано главная со словарём, где «Rückenmark» встречается чаще, чем «Rücken»', async () => {
      await openHome(page);
      await expect(valueField(page)).toBeVisible();
    });

    await test.step('Когда он печатает «Rücken»', async () => {
      await valueField(page).pressSequentially('Rücken');
      await expect(options(page)).toHaveCount(2);
    });

    await test.step('Тогда первая подсказка — «der Rücken», вторая — «das Rückenmark»', async () => {
      await expect(options(page).nth(0)).toHaveText('der Rücken');
      await expect(options(page).nth(1)).toHaveText('das Rückenmark');
    });

    await test.step('Тогда в поле перевода подставлено «спина»', async () => {
      await expect(translationField(page)).toHaveValue('спина');
    });
  });
});
