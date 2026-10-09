const { test, expect } = require('./fixtures');

// The add form auto-grows its two textareas and swaps copy the moment the
// input reads as a phrase. Both mechanics used to move the form under the
// cursor, and the measured height was short by the border box, which showed
// as a scrollbar inside a one-line field. These are geometry assertions —
// what the DOM says is not what the eye sees.
//
// Chrome-only, and that bounds what they prove: here `field-sizing: content`
// owns the height, so the JS measuring path these specs would otherwise cover
// runs on Safari alone.

test.describe('Форма добавления слова', () => {
  test('пользователь вводит и отправляет слово → поля не меняют размеры и не прыгают', async ({ page }) => {
    const word = () => page.getByLabel('Слово (немецкий)');
    const translation = page.getByLabel('Перевод (русский)');
    let emptyHeight;
    let wordBox;

    await test.step('Дано главная с пустой формой', async () => {
      await page.goto('/home');
      emptyHeight = (await translation.boundingBox()).height;
    });

    await test.step('Когда он вводит однострочный перевод', async () => {
      await translation.fill('например, к примеру');
    });

    await test.step('Тогда в поле нет полосы прокрутки', async () => {
      const overflows = await translation.evaluate((n) => n.scrollHeight > n.clientHeight);
      expect(overflows).toBe(false);
      await translation.fill('');
    });

    await test.step('Когда слово превращается во фразу', async () => {
      await word().fill('Haus');
      wordBox = await word().boundingBox();
      // The space flips detection to phrase mode and rewrites every label,
      // this field's own included; the element stays the same one.
      await word().fill('auf jeden Fall');
    });

    await test.step('Тогда поле слова остаётся на месте', async () => {
      const after = await page.getByLabel('Фраза (немецкий)').boundingBox();
      expect(after.x).toBe(wordBox.x);
      expect(after.y).toBe(wordBox.y);
    });

    await test.step('Когда он вводит длинную фразу с длинным переводом', async () => {
      await page.getByLabel('Фраза (немецкий)').fill('Entschuldigung, dass ich zu spät komme');
      await translation.fill(
        'извините, что опаздываю; простите за опоздание, я застрял в пробке ' +
          'и не успел предупредить заранее'
      );
    });

    await test.step('Тогда поле перевода выросло', async () => {
      expect((await translation.boundingBox()).height).toBeGreaterThan(emptyHeight);
    });

    await test.step('Когда он нажимает «ДОБАВИТЬ»', async () => {
      await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
    });

    await test.step('Тогда поля пусты и вернулись к исходной высоте', async () => {
      await expect(translation).toHaveValue('');
      // A height measured for the old content is an inline style; nothing
      // else clears it, so an emptied field would keep it.
      expect((await translation.boundingBox()).height).toBe(emptyHeight);
    });
  });
});
