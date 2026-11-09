const { test, expect, docsOfType } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { seedLessons } = require('./seed.shared');

// A lesson passed lights a star in the shell bar, counted for today; the end
// of a lesson offers one more (#715).

const ANSWERS = { дом: 'Haus', собака: 'Hund' };

const homeHeading = (page) => page.getByRole('heading', { name: 'Главная' });
const progress = (page) => page.getByRole('progressbar', { name: 'Прогресс урока' });
const stars = (page) => page.getByRole('img', { name: /^Уроков за сегодня/ });
const oneMore = (page) => page.getByRole('button', { name: 'ЕЩЁ УРОК' });

async function startLesson(page) {
  await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
  await expect(page).toHaveURL(/\/lesson$/);
  await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
}

// Answers every trial of the lesson on screen, two words of two trials.
async function passLesson(page) {
  for (let trial = 0; trial < 2; trial += 1) {
    await expect(page.locator('#lesson-answer')).toBeVisible();
    const prompt = (await page.locator('#lesson-challenge .lesson__prompt').textContent()).trim();
    await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(ANSWERS[prompt]);
    await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
    await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    if (trial === 0) await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  }
}

// The stars of the bar as drawn `ms` into the motion of the last change, left to right: each star's colour as
// painted (a purple star grows in gold), left edge and drawn width. The light-up of a star is run to its end;
// growth, the turn to purple and gathering are stopped at `ms`.
async function starsAt(page, ms) {
  return page.evaluate((ms) => {
    const svgs = [...document.querySelectorAll('.app-shell__stars svg')];
    for (const svg of svgs) {
      for (const animation of svg.getAnimations()) {
        if (animation.animationName === 'app-shell-star-glow') {
          animation.finish();
        } else {
          animation.pause();
          animation.currentTime = ms;
        }
      }
    }
    return svgs
      .map((svg) => {
        const box = svg.getBoundingClientRect();
        const body = getComputedStyle(svg.querySelector('.app-shell__star-body')).fill;
        return { purple: body === 'rgb(125, 79, 196)', left: box.left, width: box.width };
      })
      .sort((a, b) => a.left - b.left);
  }, ms);
}

// Notes, on the page's own clock, when each star starts to go and when it is gone: `window.__leaving`.
async function watchLeaving(page) {
  await page.evaluate(() => {
    const row = document.querySelector('.app-shell__stars');
    const leaving = new Map();
    window.__leaving = [];
    new MutationObserver((records) => {
      for (const record of records) {
        if (record.type === 'attributes' && record.target.classList.contains('app-shell__star--leaving')
            && !leaving.has(record.target)) {
          const entry = { from: performance.now(), to: null };
          leaving.set(record.target, entry);
          window.__leaving.push(entry);
        }
        for (const node of record.removedNodes) {
          if (leaving.has(node)) leaving.get(node).to = performance.now();
        }
      }
    }).observe(row, { attributes: true, attributeFilter: ['class'], childList: true, subtree: true });
  });
}

// The stars the app opens on only light up: the row takes a moment before a star that joins it grows in.
async function starsSettled(page) {
  await page.waitForFunction(() => matchMedia('(prefers-reduced-motion: reduce)').matches
    || getComputedStyle(document.querySelector('.app-shell__stars')).getPropertyValue('--app-shell-stars-open').trim() === 'done');
}

// Two words, `lessons` already passed today, and a lesson started.
async function lessonAfter(page, lessons) {
  await page.goto('/home');
  await addWord(page, 'Haus', 'дом');
  await addWord(page, 'Hund', 'собака');
  await seedLessons(page, Array(lessons).fill(0));
  await page.reload();
  await expect(stars(page)).toHaveAccessibleName(`Уроков за сегодня: ${lessons}`);
  await starsSettled(page);
  await startLesson(page);
}

test.describe('Звёзды за уроки', () => {
  test('ученик проходит урок → в шапке звезда, «ЕЩЁ УРОК» сразу начинает новый урок', async ({ page }) => {
    await test.step('Дано два слова и начатый урок', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await addWord(page, 'Hund', 'собака');
      await expect(stars(page)).toHaveCount(0);
      await startLesson(page);
    });

    await test.step('Когда он верно отвечает на последнее задание', async () => {
      await passLesson(page);
    });

    await test.step('Тогда в шапке одна звезда, а «ЕЩЁ УРОК» в фокусе над «ЗАКОНЧИТЬ»', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 1');
      await expect(oneMore(page)).toBeFocused();
      await expect(page.getByRole('button', { name: 'ЗАКОНЧИТЬ' })).toBeVisible();
      expect(await docsOfType(page, 'user-db', 'lesson')).toHaveLength(1);
    });

    await test.step('Когда он жмёт «ЕЩЁ УРОК»', async () => {
      await oneMore(page).click();
    });

    await test.step('Тогда на экране новый урок с нулевым прогрессом, прошлого урока в ленте нет, звезда на месте', async () => {
      await expect(page).toHaveURL(/\/lesson$/);
      await expect(progress(page)).toHaveAttribute('aria-valuenow', '0');
      await expect(page.locator('#lesson-answer')).toBeVisible();
      await expect(page.locator('.lesson__prompt')).toHaveCount(1);
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 1');
    });

    await test.step('Когда он проходит и его, и жмёт «ЗАКОНЧИТЬ»', async () => {
      await passLesson(page);
      await page.getByRole('button', { name: 'ЗАКОНЧИТЬ' }).click();
    });

    await test.step('Тогда он на главной, и в шапке две звезды', async () => {
      await expect(homeHeading(page)).toBeVisible();
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 2');
      await expect(stars(page).locator('svg')).toHaveCount(2);
    });
  });

  test('ученик верно отвечает на последнее задание → «ЕЩЁ УРОК» на месте «ДАЛЕЕ», «ЗАКОНЧИТЬ» в строке «Правильно!»', async ({ page }) => {
    let nextBox;

    await test.step('Дано два слова одной длины и верный ответ на первое задание', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await addWord(page, 'Hund', 'собака');
      await startLesson(page);
      const prompt = (await page.locator('#lesson-challenge .lesson__prompt').textContent()).trim();
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(ANSWERS[prompt]);
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
      nextBox = await page.getByRole('button', { name: 'ДАЛЕЕ' }).boundingBox();
      await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
    });

    await test.step('Когда он верно отвечает на последнее', async () => {
      await expect(page.locator('#lesson-answer')).toBeVisible();
      const prompt = (await page.locator('#lesson-challenge .lesson__prompt').textContent()).trim();
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(ANSWERS[prompt]);
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
    });

    await test.step('Тогда «ЕЩЁ УРОК» стоит там же, где стояла «ДАЛЕЕ», а «ЗАКОНЧИТЬ» — в строке «Правильно!»', async () => {
      await expect(oneMore(page)).toBeFocused();
      expect(await oneMore(page).boundingBox()).toEqual(nextBox);
      const verdict = await page.getByRole('heading', { name: 'Правильно!' }).boundingBox();
      const finish = await page.getByRole('button', { name: 'ЗАКОНЧИТЬ' }).boundingBox();
      expect(Math.abs((finish.y + finish.height / 2) - (verdict.y + verdict.height / 2))).toBeLessThan(2);
      expect(finish.x).toBeGreaterThan(verdict.x + verdict.width);
    });
  });

  test('ученик с уроками за сегодня и вчера → в шапке только сегодняшние, пять золотых — одна фиолетовая', async ({ page }) => {
    await test.step('Дано семь уроков сегодня и три вчера', async () => {
      await page.goto('/home');
      await expect(homeHeading(page)).toBeVisible();
      await seedLessons(page, [0, 0, 0, 0, 0, 0, 0, 1.1, 1.2, 1.3]);
    });

    await test.step('Когда он открывает приложение', async () => {
      await page.reload();
    });

    await test.step('Тогда в шапке одна фиолетовая и две золотые звезды', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 7');
      await expect(stars(page).locator('.app-shell__star--purple')).toHaveCount(1);
      await expect(stars(page).locator('svg')).toHaveCount(3);
    });
  });

  test('ученик с двадцатью шестью уроками сегодня → в шапке «5», фиолетовая и золотая звёзды', async ({ page }) => {
    await test.step('Дано двадцать шесть уроков сегодня', async () => {
      await page.goto('/home');
      await expect(homeHeading(page)).toBeVisible();
      await seedLessons(page, Array(26).fill(0));
      await page.reload();
    });

    await test.step('Тогда в шапке число 5, одна фиолетовая и одна золотая звезда', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 26');
      await expect(stars(page)).toContainText('5');
      await expect(stars(page).locator('.app-shell__star--purple')).toHaveCount(1);
      await expect(stars(page).locator('svg')).toHaveCount(2);
    });
  });

  test('ученик проходит третий урок → звезда вырастает больше своего размера и оседает, прежние уходят влево', async ({ page }) => {
    let firstBefore;

    await test.step('Дано два урока сегодня и начатый третий', async () => {
      await lessonAfter(page, 2);
      firstBefore = (await starsAt(page, 0))[0].left;
    });

    await test.step('Когда он его проходит', async () => {
      await passLesson(page);
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 3');
    });

    // A motion stopped at its end is over and cannot be wound back: the moments are taken in order.
    let moments;

    await test.step('Тогда третья звезда в середине движения крупнее своего размера, в конце — своего размера', async () => {
      moments = { halfway: await starsAt(page, 210), middle: await starsAt(page, 420), end: await starsAt(page, 600) };
      expect(moments.middle[2].width).toBeGreaterThan(25);
      expect(moments.end[2].width).toBeCloseTo(24, 0);
    });

    await test.step('И первая звезда отъезжает влево постепенно: на полпути роста она между прежним и новым местом', async () => {
      expect(moments.end[0].left).toBeLessThan(firstBefore - 5);
      expect(moments.halfway[0].left).toBeLessThan(firstBefore - 2);
      expect(moments.halfway[0].left).toBeGreaterThan(moments.end[0].left + 2);
    });
  });

  test('ученик проходит пятый урок → пятая звезда вырастает золотой справа, потом четыре золотые собираются в неё, и она фиолетовая', async ({ page }) => {
    await test.step('Дано четыре урока сегодня и начатый пятый', async () => {
      await lessonAfter(page, 4);
    });

    await test.step('Когда он его проходит', async () => {
      await passLesson(page);
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 5');
    });

    await test.step('Тогда сначала в шапке пять золотых звёзд, фиолетовой не видно', async () => {
      const grown = await starsAt(page, 550);
      expect(grown.filter((star) => !star.purple && star.width > 20)).toHaveLength(5);
      expect(grown.filter((star) => star.purple && star.width > 1)).toHaveLength(0);
    });

    await test.step('А в конце — одна фиолетовая там, где выросла пятая, золотых не видно', async () => {
      const grown = await starsAt(page, 550);
      const end = await starsAt(page, 2000);
      expect(end.filter((star) => star.purple && star.width > 20)).toHaveLength(1);
      expect(end.filter((star) => !star.purple && star.width > 1)).toHaveLength(0);
      expect(grown[4].left).toBeGreaterThan(grown[3].left);
    });
  });

  test('ученик проходит пятый урок → четыре золотые стоят в ряду, пока собираются, и уходят только потом', async ({ page }) => {
    await test.step('Дано четыре урока сегодня, начатый пятый и отметки, когда звёзды уходят', async () => {
      await lessonAfter(page, 4);
      await watchLeaving(page);
    });

    await test.step('Когда он его проходит', async () => {
      await passLesson(page);
      await page.waitForFunction(() => window.__leaving.length === 4 && window.__leaving.every((entry) => entry.to));
    });

    await test.step('Тогда каждая золотая уходит из ряда не раньше, чем через секунду: пятая выросла, четыре собрались', async () => {
      const stays = await page.evaluate(() => window.__leaving.map((entry) => entry.to - entry.from));
      expect(stays.filter((ms) => ms < 1000)).toEqual([]);
    });
  });

  test('ученик заканчивает урок и сразу жмёт Escape → на главной новая звезда дорастает', async ({ page }) => {
    await test.step('Дано два урока сегодня и начатый третий', async () => {
      await lessonAfter(page, 2);
    });

    await test.step('Когда он его проходит и сразу жмёт Escape', async () => {
      await passLesson(page);
      await page.keyboard.press('Escape');
      await expect(homeHeading(page)).toBeVisible();
    });

    await test.step('Тогда на главной третья звезда в середине роста крупнее своего размера, в конце — своего', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 3');
      expect((await starsAt(page, 420))[2].width).toBeGreaterThan(25);
      expect((await starsAt(page, 600))[2].width).toBeCloseTo(24, 0);
    });
  });

  test('ученик на главной, урок с другого устройства приходит в шапку → его звезда вырастает', async ({ page }) => {
    await test.step('Дано главная с двумя уроками сегодня', async () => {
      await page.goto('/home');
      await expect(homeHeading(page)).toBeAttached();
      await seedLessons(page, [0, 0]);
      await page.reload();
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 2');
      await starsSettled(page);
    });

    await test.step('Когда урок, пройденный на другом устройстве, доходит до памяти', async () => {
      await page.evaluate(async () => {
        await db.bulk_docs(db.use('user-db'), [{ _id: 'lesson-elsewhere', type: 'lesson', passed_at: new Date().toISOString() }]);
      });
    });

    await test.step('Тогда третья звезда в середине роста крупнее своего размера', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 3');
      expect((await starsAt(page, 420))[2].width).toBeGreaterThan(25);
    });
  });

  test('ученик проходит двадцать пятый урок → четыре фиолетовые и пять золотых собираются в «5» и одну фиолетовую', async ({ page }) => {
    await test.step('Дано двадцать четыре урока сегодня и начатый двадцать пятый', async () => {
      await lessonAfter(page, 24);
    });

    await test.step('Когда он его проходит', async () => {
      await passLesson(page);
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 25');
    });

    await test.step('Тогда сначала в шапке четыре фиолетовые и пять золотых звёзд', async () => {
      const grown = await starsAt(page, 550);
      expect(grown.filter((star) => star.purple && star.width > 20)).toHaveLength(4);
      expect(grown.filter((star) => !star.purple && star.width > 20)).toHaveLength(5);
    });

    await test.step('А в конце — «5» и одна фиолетовая, золотых не видно', async () => {
      const end = await starsAt(page, 2000);
      expect(end.filter((star) => star.purple && star.width > 20)).toHaveLength(1);
      expect(end.filter((star) => !star.purple && star.width > 1)).toHaveLength(0);
      await expect(stars(page)).toContainText('5');
    });
  });

  test('ученик на узком телефоне проходит двадцать пятый урок → крайние звёзды заходят за края середины шапки, не обрезаясь', async ({ page }) => {
    let slot;
    let first;

    await test.step('Дано окно шириной с телефон, двадцать четыре урока сегодня и начатый двадцать пятый', async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await lessonAfter(page, 24);
    });

    await test.step('Когда он его проходит и девять звёзд стоят в ряд', async () => {
      await passLesson(page);
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 25');
      first = (await starsAt(page, 550))[0];
      slot = await page.locator('.app-shell__info-bar').boundingBox();
    });

    await test.step('Тогда первая звезда начинается левее середины шапки и видна там, пока не собралась', async () => {
      expect(first.left).toBeLessThan(slot.x - 4);
      const clip = { x: first.left, y: slot.y, width: slot.x - first.left, height: slot.height };
      const moving = await page.screenshot({ clip });
      await starsAt(page, 2000);
      const gathered = await page.screenshot({ clip });
      expect(moving.equals(gathered)).toBe(false);
    });
  });

  test('ученик открывает приложение с пятью уроками сегодня → одна фиолетовая звезда, не растёт, золотые не собираются', async ({ page }) => {
    await test.step('Дано пять уроков сегодня', async () => {
      await page.goto('/home');
      await expect(homeHeading(page)).toBeVisible();
      await seedLessons(page, Array(5).fill(0));
    });

    await test.step('Когда он открывает приложение', async () => {
      await page.reload();
    });

    await test.step('Тогда в шапке одна фиолетовая звезда и ничего больше', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 5');
      await expect(stars(page).locator('svg')).toHaveCount(1);
      await expect(stars(page).locator('.app-shell__star--purple')).toHaveCount(1);
    });

    await test.step('И она не растёт: с первого мига своего размера и фиолетовая', async () => {
      const [star] = await starsAt(page, 0);
      expect(star.width).toBeGreaterThan(20);
      expect(star.purple).toBe(true);
    });
  });

  test('ученик, попросивший меньше движения, проходит пятый урок → сразу одна фиолетовая звезда', async ({ page }) => {
    await test.step('Дано меньше движения, четыре урока сегодня и начатый пятый', async () => {
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await lessonAfter(page, 4);
    });

    await test.step('Когда он его проходит', async () => {
      await passLesson(page);
    });

    await test.step('Тогда в шапке видна одна фиолетовая звезда и ни одной золотой', async () => {
      await expect(stars(page)).toHaveAccessibleName('Уроков за сегодня: 5');
      await expect(stars(page).locator('svg:visible')).toHaveCount(1);
      await expect(stars(page).locator('.app-shell__star--purple:visible')).toHaveCount(1);
    });
  });
});
