const fs = require('fs');
const { test, expect, nothingHappensFor } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// The statistics a tap on today's stars opens (#778): the words by memory, the lessons by day, and a picture to
// share.

const statsButton = (page) => page.getByRole('button', { name: 'Статистика' });
const days = (page) => page.locator('.statistics__day');
const wordField = (page) => page.getByLabel('Слово (немецкий)');

// Lessons passed, written straight into the user database: `ages` are how many local days before today each was
// passed, now for 0, at noon for another day, so no change of the clocks moves a lesson to a day beside it.
async function seedLessons(page, ages) {
  await page.evaluate(async (ages) => {
    const passedAt = (age) => {
      if (age === 0) return new Date();
      const noon = new Date();
      noon.setHours(12, 0, 0, 0);
      noon.setDate(noon.getDate() - age);
      return noon;
    };
    const docs = ages.map((age, index) => ({
      _id: 'lesson-seed-' + index,
      type: 'lesson',
      passed_at: passedAt(age).toISOString(),
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, ages);
}

// Home with `ages` of lessons, opened again so it reads them.
async function homeWithLessons(page, ages) {
  await page.goto('/home');
  await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
  await seedLessons(page, ages);
  await page.reload();
  await expect(statsButton(page)).toBeVisible();
}

test.describe('Статистика', () => {
  test('ученик нажимает на звёзды на главной → статистика, Escape возвращает на главную', async ({ page }) => {
    await test.step('Дано два урока сегодня', async () => {
      await homeWithLessons(page, [0, 0]);
    });

    await test.step('Когда он нажимает на звёзды', async () => {
      await statsButton(page).click();
    });

    await test.step('Тогда на экране статистика', async () => {
      await expect(page).toHaveURL(/\/stats$/);
      await expect(page.getByRole('heading', { name: 'Уроков пройдено: 2' })).toBeVisible();
    });

    await test.step('Когда он нажимает Escape', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда он на главной', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('ученик жмёт Alt+S в поле слова на главной → статистика, в поле ничего не напечатано', async ({ page }) => {
    await test.step('Дано урок сегодня, курсор в поле слова', async () => {
      await homeWithLessons(page, [0]);
      await wordField(page).focus();
    });

    await test.step('Когда он жмёт Alt+S', async () => {
      await page.keyboard.press('Alt+KeyS');
    });

    await test.step('Тогда на экране статистика', async () => {
      await expect(page).toHaveURL(/\/stats$/);
      await expect(page.getByRole('heading', { name: 'Уроков пройдено: 1' })).toBeVisible();
    });

    await test.step('Когда он нажимает Escape', async () => {
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда он на главной, поле слова пустое', async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(wordField(page)).toHaveValue('');
    });
  });

  test('ученик жмёт Alt+S в поиске слов и в темах → статистика, Escape возвращает на тот же экран', async ({ page }) => {
    await test.step('Дано слово, урок сегодня и открытый список слов, курсор в поиске', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await seedLessons(page, [0]);
      await page.reload();
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(page).toHaveURL(/\/words$/);
      await page.getByPlaceholder('Поиск').focus();
    });

    await test.step('Когда он жмёт Alt+S, затем Escape', async () => {
      await page.keyboard.press('Alt+KeyS');
      await expect(page).toHaveURL(/\/stats$/);
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда он в списке слов, поиск пустой', async () => {
      await expect(page).toHaveURL(/\/words$/);
      await expect(page.getByPlaceholder('Поиск')).toHaveValue('');
    });

    await test.step('Когда он уходит в темы и жмёт Alt+S, затем Escape', async () => {
      await page.keyboard.press('Escape');
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await expect(page).toHaveURL(/\/collections$/);
      await page.keyboard.press('Alt+KeyS');
      await expect(page).toHaveURL(/\/stats$/);
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда он в темах', async () => {
      await expect(page).toHaveURL(/\/collections$/);
    });
  });

  test('ученик без уроков сегодня жмёт Alt+S на главной → остаётся на главной', async ({ page }) => {
    await test.step('Дано главная без уроков сегодня', async () => {
      await page.goto('/home');
      await wordField(page).focus();
    });

    await test.step('Когда он жмёт Alt+S', async () => {
      await page.keyboard.press('Alt+KeyS');
      await nothingHappensFor(page, 500);
    });

    await test.step('Тогда он на главной', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('ученик открывает статистику из списка слов и нажимает «Назад» → список слов, ещё раз «Назад» → главная', async ({ page }) => {
    await test.step('Дано слово, урок сегодня и открытый список слов', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await seedLessons(page, [0]);
      await page.reload();
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(page).toHaveURL(/\/words$/);
    });

    await test.step('Когда он открывает статистику и нажимает «Назад» браузера', async () => {
      await statsButton(page).click();
      await expect(page).toHaveURL(/\/stats$/);
      await page.goBack();
    });

    await test.step('Тогда он в списке слов', async () => {
      await expect(page).toHaveURL(/\/words$/);
      await expect(page.getByPlaceholder('Поиск')).toBeVisible();
    });

    await test.step('Когда он ещё раз нажимает «Назад»', async () => {
      await page.goBack();
    });

    await test.step('Тогда он на главной', async () => {
      await expect(page).toHaveURL(/\/home$/);
    });
  });

  test('ученик закрывает крестиком статистику, открытую из наборов → наборы', async ({ page }) => {
    await test.step('Дано урок сегодня и открытые наборы', async () => {
      await homeWithLessons(page, [0]);
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await expect(page).toHaveURL(/\/collections$/);
    });

    await test.step('Когда он открывает статистику и нажимает «Закрыть»', async () => {
      await statsButton(page).click();
      await expect(page).toHaveURL(/\/stats$/);
      await page.getByRole('button', { name: 'Закрыть' }).click();
    });

    await test.step('Тогда он в наборах', async () => {
      await expect(page).toHaveURL(/\/collections$/);
    });
  });

  test('ученик в уроке → звёзды не открывают статистику', async ({ page }) => {
    await test.step('Дано два слова и урок сегодня', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await addWord(page, 'Hund', 'собака');
      await seedLessons(page, [0]);
      await page.reload();
      await expect(statsButton(page)).toBeVisible();
    });

    await test.step('Когда он начинает урок', async () => {
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
      await expect(page).toHaveURL(/\/lesson$/);
    });

    await test.step('Тогда звёзды на месте, а кнопки статистики нет', async () => {
      await expect(page.getByRole('img', { name: 'Уроков за сегодня: 1' })).toBeVisible();
      await expect(statsButton(page)).toHaveCount(0);
    });
  });

  test('ученик с двумя неотвеченными словами открывает статистику → «Слов: 2», оба в первом столбике и в «новых»', async ({ page }) => {
    await test.step('Дано два слова без ответов и урок сегодня', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await addWord(page, 'Hund', 'собака');
      await seedLessons(page, [0]);
      await page.reload();
    });

    await test.step('Когда он открывает статистику', async () => {
      await statsButton(page).click();
    });

    await test.step('Тогда слов 2, первый столбик — 2, остальные — 0, «новые» — 2', async () => {
      await expect(page.getByRole('heading', { name: 'Слов: 2' })).toBeVisible();
      await expect(page.locator('.statistics__bin-count')).toHaveText(['2', '0', '0', '0', '0', '0', '0', '0', '0', '0']);
      await expect(page.getByRole('listitem').filter({ hasText: 'новые' })).toHaveText('2новые');
      await expect(page.getByRole('listitem').filter({ hasText: 'отлично' })).toHaveText('0отлично');
    });
  });

  test('ученик с уроками за сегодня и позавчера открывает статистику → три дня, позавчера фиолетовый', async ({ page }) => {
    await test.step('Дано два урока сегодня и пять позавчера', async () => {
      await homeWithLessons(page, [0, 0, 2, 2, 2, 2, 2]);
    });

    await test.step('Когда он открывает статистику', async () => {
      await statsButton(page).click();
    });

    await test.step('Тогда уроков 7, дней три: позавчера 5 фиолетовым, вчера пусто, сегодня 2 и отмечено', async () => {
      await expect(page.getByRole('heading', { name: 'Уроков пройдено: 7' })).toBeVisible();
      await expect(days(page)).toHaveCount(3);
      await expect(page.locator('.statistics__day-lessons')).toHaveText(['5', '', '2']);
      await expect(days(page).first().locator('.statistics__day-fill--purple')).toHaveCount(1);
      await expect(days(page).last().locator('.statistics__day-date--today')).toHaveCount(1);
    });
  });

  test('ученик на телефоне с уроками за два месяца открывает статистику → лента стоит на сегодня', async ({ page }) => {
    await test.step('Дано окно телефона, урок шестьдесят дней назад и сегодня', async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await homeWithLessons(page, [60, 0]);
    });

    await test.step('Когда он открывает статистику', async () => {
      await statsButton(page).click();
    });

    await test.step('Тогда сегодняшний столбик виден у правого края', async () => {
      await expect(days(page)).toHaveCount(61);
      await expect(days(page).last()).toBeInViewport();
      await expect(days(page).first()).not.toBeInViewport();
    });
  });

  test('ученик нажимает «ПОДЕЛИТЬСЯ» → системе отдана картинка PNG 1080 на 1350', async ({ page }) => {
    await test.step('Дано системное меню «Поделиться» и открытая статистика', async () => {
      await page.addInitScript(() => {
        navigator.canShare = () => true;
        navigator.share = async ({ files }) => {
          const bitmap = await createImageBitmap(files[0]);
          window.__shared = { type: files[0].type, width: bitmap.width, height: bitmap.height, count: files.length };
        };
      });
      await homeWithLessons(page, [0]);
      await statsButton(page).click();
    });

    await test.step('Когда он нажимает «ПОДЕЛИТЬСЯ»', async () => {
      await page.getByRole('button', { name: 'ПОДЕЛИТЬСЯ' }).click();
    });

    await test.step('Тогда меню получило один PNG 1080 на 1350', async () => {
      await expect.poll(() => page.evaluate(() => window.__shared)).toEqual({ type: 'image/png', width: 1080, height: 1350, count: 1 });
    });
  });

  test('ученик с тёмной темой нажимает «ПОДЕЛИТЬСЯ» → картинка на ночном фоне со светлым текстом', async ({ page }) => {
    await test.step('Дано тёмная тема телефона, системное меню «Поделиться» и открытая статистика', async () => {
      await page.emulateMedia({ colorScheme: 'dark' });
      // The shared picture's ground at its corner and the brightest pixel of its title, «Шпреха» at the top left.
      await page.addInitScript(() => {
        navigator.canShare = () => true;
        navigator.share = async ({ files }) => {
          const bitmap = await createImageBitmap(files[0]);
          const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
          const context = canvas.getContext('2d');
          context.drawImage(bitmap, 0, 0);
          const ground = [...context.getImageData(4, 4, 1, 1).data.slice(0, 3)];
          const title = context.getImageData(66, 70, 300, 70).data;
          let brightest = 0;
          for (let i = 0; i < title.length; i += 4) brightest = Math.max(brightest, title[i] + title[i + 1] + title[i + 2]);
          window.__shared = { ground, lightText: brightest > 3 * 200 };
        };
      });
      await homeWithLessons(page, [0]);
      await statsButton(page).click();
    });

    await test.step('Когда он нажимает «ПОДЕЛИТЬСЯ»', async () => {
      await page.getByRole('button', { name: 'ПОДЕЛИТЬСЯ' }).click();
    });

    await test.step('Тогда картинка на ночном фоне #150f1c, и заголовок светлый', async () => {
      await expect.poll(() => page.evaluate(() => window.__shared)).toEqual({ ground: [21, 15, 28], lightText: true });
    });
  });

  test('ученик на телефоне листает ленту за два месяца → название месяца стоит у левого края, пока его дни видны', async ({ page }) => {
    await test.step('Дано окно телефона, урок шестьдесят дней назад и сегодня, открытая статистика', async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await homeWithLessons(page, [60, 0]);
      await statsButton(page).click();
      await expect(days(page).last()).toBeInViewport();
    });

    await test.step('Когда он отводит ленту на две недели назад', async () => {
      await page.locator('.statistics__days').evaluate((ribbon) => { ribbon.scrollLeft -= 14 * 40; });
    });

    await test.step('Тогда месяц, чьи дни у левого края, назван у левого края, хотя его первый день ушёл за край', async () => {
      const { atEdge, left } = await page.locator('.statistics__days').evaluate((ribbon) => {
        const left = ribbon.getBoundingClientRect().left + parseFloat(getComputedStyle(ribbon).paddingLeft);
        const atEdge = [...ribbon.querySelectorAll('.statistics__month')]
          .map((month) => ({ box: month.getBoundingClientRect(), name: month.querySelector('.statistics__month-name').getBoundingClientRect() }))
          .find(({ box }) => box.left < left && box.right > left + 40);
        return { atEdge, left };
      });
      expect(atEdge).toBeDefined();
      expect(Math.abs(atEdge.name.left - left)).toBeLessThan(2);
    });
  });

  test('ученик открывает /stats адресом и закрывает крестиком → главная', async ({ page }) => {
    await test.step('Дано урок сегодня', async () => {
      await homeWithLessons(page, [0]);
    });

    await test.step('Когда он открывает статистику прямо по адресу и нажимает «Закрыть»', async () => {
      await page.goto('/stats');
      await expect(page.getByRole('heading', { name: 'Уроков пройдено: 1' })).toBeVisible();
      await page.getByRole('button', { name: 'Закрыть' }).click();
    });

    await test.step('Тогда он на главной', async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
    });
  });

  test('ученик с уроком, датированным завтра (часы другого устройства спешат) → урок в счёте и в ленте, отмечено сегодня', async ({ page }) => {
    await test.step('Дано урок сегодня и урок завтрашним днём', async () => {
      await homeWithLessons(page, [0, -1]);
    });

    await test.step('Когда он открывает статистику', async () => {
      await statsButton(page).click();
    });

    await test.step('Тогда уроков 2, дня в ленте два, и сегодняшний — первый из них', async () => {
      await expect(page.getByRole('heading', { name: 'Уроков пройдено: 2' })).toBeVisible();
      await expect(days(page)).toHaveCount(2);
      await expect(days(page).first().locator('.statistics__day-date--today')).toHaveCount(1);
    });
  });

  test('ученик в браузере без меню «Поделиться» нажимает «ПОДЕЛИТЬСЯ» → картинка скачивается', async ({ page }) => {
    await test.step('Дано браузер, который не умеет делиться файлами, и открытая статистика', async () => {
      await page.addInitScript(() => {
        navigator.canShare = undefined;
      });
      await homeWithLessons(page, [0]);
      await statsButton(page).click();
    });

    const download = page.waitForEvent('download');

    await test.step('Когда он нажимает «ПОДЕЛИТЬСЯ»', async () => {
      await page.getByRole('button', { name: 'ПОДЕЛИТЬСЯ' }).click();
    });

    await test.step('Тогда скачан PNG 1080 на 1350', async () => {
      const file = await download;
      expect(file.suggestedFilename()).toBe('sprecha.png');
      const png = fs.readFileSync(await file.path());
      expect(png.subarray(1, 4).toString()).toBe('PNG');
      expect([png.readUInt32BE(16), png.readUInt32BE(20)]).toEqual([1080, 1350]);
    });
  });
});
