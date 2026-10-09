const { test, expect } = require('./fixtures');
const { setUpLesson, token, answerWidths } = require('./lesson-answer.shared');

// Answer-hint popover in the lesson (GH-273): the revealed example answer
// carries clickable annotated words; a card offers adding an unknown word or
// says the word is already known; the card appears on token click and hides on
// a click outside or after the pointer leaves it. Lesson setup lives in
// lesson-answer.shared.js.

const popover = (page) => page.locator('#popover');
const SENTENCE = 'Der Hund schläft im Garten.';

test.describe('Подсказки к словам в разборе ответа', () => {
  test('пользователь верно отвечает на пример → видит «Правильно!» и слова с подсказками', async ({ page }) => {
    await test.step('Когда он верно отвечает на предложение', async () => {
      await setUpLesson(page, SENTENCE);
    });

    await test.step('Тогда видно «Правильно!» и кликабельные слова', async () => {
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
      await expect(token(page, 1)).toHaveText('Hund');
      await expect(token(page, 4)).toHaveText('Garten.');
    });
  });

  test('пользователь неверно отвечает на пример → видит эталон со словами с подсказками', async ({ page }) => {
    await test.step('Когда он неверно отвечает на предложение', async () => {
      await setUpLesson(page, 'Die Katze schläft.');
    });

    await test.step('Тогда видно «Правильно:» и эталон со словами с подсказками', async () => {
      await expect(page.getByRole('heading', { name: 'Правильно:' })).toBeVisible();
      await expect(token(page, 4)).toHaveText('Garten.');
    });
  });

  test('пользователь нажимает на слово в разборе → для неизвестного предлагается добавить, для известного «уже в словаре»', async ({ page }) => {
    await test.step('Дано разбор ответа', async () => {
      await setUpLesson(page, SENTENCE);
    });

    await test.step('Когда он нажимает на неизвестное слово «Garten»', async () => {
      await token(page, 4).click();
    });

    await test.step('Тогда карточка предлагает «+ В СЛОВАРЬ»', async () => {
      await expect(popover(page).locator('.token-card__word')).toHaveText('der Garten');
      await expect(popover(page).getByRole('button', { name: '+ В СЛОВАРЬ' })).toBeVisible();
    });

    await test.step('Когда он нажимает на известное слово «Hund»', async () => {
      await token(page, 1).click();
    });

    await test.step('Тогда карточка пишет «✓ В словаре» и без кнопок', async () => {
      await expect(popover(page).locator('.token-card__word')).toHaveText('der Hund');
      await expect(popover(page).locator('.token-card__state')).toHaveText('✓ В словаре');
      await expect(popover(page).getByRole('button')).toHaveCount(0);
    });
  });

  test('пользователь добавляет слово из карточки → карточка обновляется на месте, слово в списке', async ({ page }) => {
    await test.step('Дано карточка неизвестного слова «Garten»', async () => {
      await setUpLesson(page, SENTENCE);
      await token(page, 4).click();
    });

    await test.step('Когда он нажимает «+ В СЛОВАРЬ»', async () => {
      await popover(page).getByRole('button', { name: '+ В СЛОВАРЬ' }).click();
    });

    await test.step('Тогда та же карточка показывает «✓ В словаре»', async () => {
      await expect(popover(page).locator('.token-card__state')).toHaveText('✓ В словаре');
      await expect(popover(page).locator('.token-card__word')).toHaveText('der Garten');
    });

    await test.step('Когда он открывает список слов', async () => {
      await page.goto('/words');
    });

    await test.step('Тогда там есть «der Garten»', async () => {
      await expect(page.locator('.word-item__value').filter({ hasText: 'der Garten' })).toBeVisible();
    });
  });

  test('пользователь выделяет разбор ответа мышью → выделяется сплошной текст через слова с подсказками', async ({ page }) => {
    await test.step('Дано разбор ответа', async () => {
      await setUpLesson(page, SENTENCE);
    });

    await test.step('Когда он протягивает мышь по всей строке', async () => {
      const box = await page.locator('.lesson__answer-body').boundingBox();
      await page.mouse.move(box.x + 1, box.y + box.height / 2);
      await page.mouse.down();
      await page.mouse.move(box.x + box.width - 1, box.y + box.height / 2, { steps: 8 });
      await page.mouse.up();
    });

    await test.step('Тогда выделено всё предложение целиком', async () => {
      const selected = await page.evaluate(() => window.getSelection().toString());
      expect(selected.replace(/\s+/g, ' ').trim()).toContain(SENTENCE);
    });
  });

  // The user's answer and the reference are compared by eye to find where they
  // differ, so a hinted word must add no width of its own (#409). Checked at
  // rest, hovered, and with its hint open.
  test('пользователь сверяет свой ответ с эталоном → слова с подсказками не шире обычного текста, расхождение видно глазом', async ({ page }) => {
    const expectSameWidth = async () => {
      const { hinted, plain } = await answerWidths(page);
      expect(Math.abs(hinted - plain)).toBeLessThanOrEqual(0.5);
    };

    await test.step('Дано разбор ответа со словами с подсказками', async () => {
      await setUpLesson(page, SENTENCE);
      await expect(token(page, 4)).toBeVisible();
    });

    await test.step('Тогда строка так же широка, как обычный текст', async () => {
      await expectSameWidth();
    });

    await test.step('Когда он наводит мышь на слово', async () => {
      await token(page, 1).hover();
    });

    await test.step('Тогда ширина та же', async () => {
      await expectSameWidth();
    });

    await test.step('Когда он открывает подсказку слова', async () => {
      await token(page, 4).click();
      await expect(token(page, 4)).toHaveAttribute('aria-expanded', 'true');
    });

    await test.step('Тогда ширина та же', async () => {
      await expectSameWidth();
    });
  });

  test('пользователь открыл подсказку и кликнул мимо или увёл мышь → подсказка закрывается', async ({ page }) => {
    await test.step('Дано разбор ответа с открытой подсказкой', async () => {
      await setUpLesson(page, SENTENCE);
      await token(page, 4).click();
      await expect(popover(page)).toBeVisible();
    });

    await test.step('Когда он кликает вне подсказки', async () => {
      await page.locator('.lesson__prompt').click();
    });

    await test.step('Тогда подсказка закрывается сразу', async () => {
      await expect(popover(page)).toBeHidden({ timeout: 500 });
    });

    await test.step('Когда он снова открывает подсказку, держит на ней мышь и уводит её', async () => {
      await token(page, 4).click();
      await expect(popover(page)).toBeVisible();
      const card = await popover(page).locator('.token-card').boundingBox();
      await page.mouse.move(card.x + card.width / 2, card.y + card.height / 2);
      await page.mouse.move(5, 5);
    });

    await test.step('Тогда через некоторое время подсказка закрывается', async () => {
      await expect(popover(page)).toBeHidden({ timeout: 3000 });
    });
  });
});
