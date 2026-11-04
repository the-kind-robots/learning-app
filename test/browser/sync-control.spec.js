const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { stubAccountCopy } = require('./account.shared');

// The lesson's light bulb green, and the grey of a control that is off.
const GREEN = 'rgb(88, 204, 2)';
const GREY = 'rgb(175, 175, 175)';
// The orange of a device whose account cannot reach the network.
const ORANGE = 'rgb(255, 150, 0)';

// An account is adopted from the recovery link `/#key=<token>`: the app asks
// `/api/identity/account` which account the token belongs to. A real account
// needs a burned invite grant, absent in a fresh environment, so that one
// answer is stubbed; the rest of the boot is the real one.
const signIn = async (page) => {
  await page.route('**/api/identity/account', (route) =>
    route.fulfill({ json: { id: 7 } }));
  await page.goto('/#key=test-token');
};

const syncButton = (page) => page.getByRole('button', { name: 'Синхронизация' });

// Screens that take the whole page: the button that opens each, and what shows it open.
const closeButton = (page) => page.getByRole('button', { name: 'Закрыть' });
const SCREENS = [
  ['список слов', 'Список слов', closeButton],
  ['наборы', 'Открыть наборы', closeButton],
  ['урок', 'НАЧАТЬ УРОК', (page) => page.getByRole('progressbar', { name: 'Прогресс урока' })],
];

test.describe('Кнопка «Синхронизация»', () => {
  test('пользователь с аккаунтом открывает главную → кнопка видна', async ({ page }) => {
    await test.step('Дано пользователь с аккаунтом', async () => {
      await signIn(page);
    });

    await test.step('Когда он на главной', async () => {
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
    });

    await test.step('Тогда кнопка «Синхронизация» видна', async () => {
      await expect(syncButton(page)).toBeVisible();
    });
  });

  test('пользователь без аккаунта открывает главную → кнопка видна и серая', async ({ page }) => {
    await test.step('Дано пользователь без аккаунта', async () => {
      await page.goto('/home');
    });

    await test.step('Тогда на главной кнопка «Синхронизация» видна и серая, без свечения', async () => {
      await expect(syncButton(page)).toBeVisible();
      await expect(syncButton(page)).toHaveCSS('color', GREY);
      await expect(syncButton(page)).toHaveCSS('filter', 'none');
    });
  });

  test('пользователь с аккаунтом открывает слова, наборы или урок → кнопки нет', async ({ page }) => {
    await test.step('Дано пользователь с аккаунтом и словом «Haus» на главной', async () => {
      await signIn(page);
      await expect(syncButton(page)).toBeVisible();
      await addWord(page, 'Haus', 'дом');
    });

    for (const [screen, opener, shown] of SCREENS) {
      await test.step(`Когда он открывает ${screen}`, async () => {
        await page.getByRole('button', { name: opener }).click();
        await expect(shown(page)).toBeVisible();
      });

      await test.step('Тогда кнопки «Синхронизация» нет', async () => {
        await expect(syncButton(page)).toHaveCount(0);
      });

      await test.step('Когда он закрывает экран', async () => {
        await page.getByRole('button', { name: 'Закрыть' }).click();
        await expect(syncButton(page)).toBeVisible();
      });
    }
  });
});

test.describe('Иконка синхронизации', () => {
  test('пользователь с аккаунтом в сети → иконка рядом с лого, зелёная со свечением', async ({ page }) => {
    await test.step('Дано пользователь с аккаунтом в сети', async () => {
      await stubAccountCopy(page.context());
      await signIn(page);
    });

    await test.step('Тогда иконка стоит сразу за лого, зелёная со свечением, и называет себя включённой', async () => {
      await expect(syncButton(page)).toHaveCSS('color', GREEN);
      await expect(syncButton(page)).not.toHaveCSS('filter', 'none');
      await expect(syncButton(page)).toHaveAccessibleName('Синхронизация включена');
      const logo = await page.getByRole('button', { name: /^(Шпреха|Sprecha)/ }).boundingBox();
      const icon = await syncButton(page).boundingBox();
      expect(icon.x - (logo.x + logo.width)).toBeGreaterThanOrEqual(0);
      expect(icon.x - (logo.x + logo.width)).toBeLessThan(16);
      expect(Math.abs(icon.y + icon.height / 2 - (logo.y + logo.height / 2))).toBeLessThan(2);
    });
  });

  test('пользователь с аккаунтом теряет сеть и находит её снова → иконка оранжевая, потом снова зелёная со свечением', async ({ page, context }) => {
    await test.step('Дано пользователь с аккаунтом в сети', async () => {
      await stubAccountCopy(context);
      await signIn(page);
      await expect(syncButton(page)).toHaveCSS('color', GREEN);
    });

    await test.step('Когда сеть пропадает', async () => {
      await context.setOffline(true);
    });

    await test.step('Тогда иконка оранжевая, без свечения, и говорит, что сети нет', async () => {
      await expect(syncButton(page)).toHaveCSS('color', ORANGE);
      await expect(syncButton(page)).toHaveCSS('filter', 'none');
      await expect(syncButton(page)).toHaveAccessibleName('Синхронизация: нет сети');
    });

    await test.step('Когда сеть возвращается', async () => {
      await context.setOffline(false);
    });

    await test.step('Тогда иконка снова зелёная со свечением', async () => {
      await expect(syncButton(page)).toHaveCSS('color', GREEN);
      await expect(syncButton(page)).not.toHaveCSS('filter', 'none');
    });
  });
});

test.describe('Меню синхронизации', () => {
  const home = (page) => page.getByRole('heading', { name: 'Главная' });
  const syncMenu = (page) => page.getByRole('dialog', { name: 'Синхронизация' });

  const openMenu = async (page) => {
    await page.goto('/home');
    await syncButton(page).click();
    await expect(syncMenu(page)).toBeVisible();
  };

  test('пользователь нажимает иконку синхронизации → меню под ней, без «Отмены», главная на месте', async ({ page }) => {
    await test.step('Дано пользователь на главной', async () => {
      await page.goto('/home');
    });

    await test.step('Когда он нажимает иконку синхронизации', async () => {
      await syncButton(page).click();
    });

    await test.step('Тогда меню открыто под иконкой, «Отмены» в нём нет, главная видна', async () => {
      await expect(syncMenu(page)).toBeVisible();
      const icon = await syncButton(page).boundingBox();
      const menu = await syncMenu(page).boundingBox();
      expect(menu.y).toBeGreaterThanOrEqual(icon.y + icon.height);
      expect(menu.x).toBeLessThanOrEqual(icon.x + icon.width / 2);
      expect(menu.x + menu.width).toBeGreaterThanOrEqual(icon.x + icon.width / 2);
      await expect(syncMenu(page).getByRole('button', { name: 'Отмена' })).toHaveCount(0);
      await expect(home(page)).toBeVisible();
    });
  });

  test('пользователь нажимает мимо меню или Esc → меню закрыто, главная на месте', async ({ page }) => {
    await test.step('Дано открытое меню синхронизации', async () => {
      await openMenu(page);
    });

    await test.step('Когда он нажимает в низ экрана, мимо меню', async () => {
      await page.mouse.click(640, 650);
    });

    await test.step('Тогда меню нет, а главная видна', async () => {
      await expect(syncMenu(page)).toHaveCount(0);
      await expect(home(page)).toBeVisible();
    });

    await test.step('Когда он снова открывает меню и нажимает Esc', async () => {
      await syncButton(page).click();
      await expect(syncMenu(page)).toBeVisible();
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда меню снова нет', async () => {
      await expect(syncMenu(page)).toHaveCount(0);
    });
  });

  test('пользователь нажимает иконку синхронизации при открытом меню → меню закрывается', async ({ page }) => {
    await test.step('Дано открытое меню синхронизации', async () => {
      await openMenu(page);
    });

    await test.step('Когда он снова нажимает иконку', async () => {
      await syncButton(page).click();
    });

    await test.step('Тогда меню нет', async () => {
      await expect(syncMenu(page)).toHaveCount(0);
    });
  });

  test('пользователь с аккаунтом выбирает «Подключить устройство» → меню закрыто, виден QR-код', async ({ page }) => {
    await test.step('Дано пользователь с аккаунтом открыл меню синхронизации', async () => {
      await stubAccountCopy(page.context());
      await signIn(page);
      await expect(syncButton(page)).toHaveCSS('color', GREEN);
      await syncButton(page).click();
    });

    await test.step('Когда он выбирает «Подключить устройство»', async () => {
      await syncMenu(page).getByRole('button', { name: 'Подключить устройство' }).click();
    });

    await test.step('Тогда меню нет, а QR-код виден', async () => {
      await expect(page.getByRole('img', { name: 'QR-код для подключения устройства' })).toBeVisible();
      await expect(syncMenu(page)).toHaveCount(0);
    });
  });
});

test.describe('Сохранить мои слова', () => {
  const syncDialog = (page) => page.getByRole('dialog', { name: 'Чтобы слова не потерялись' });

  const keepWords = (page) => page.getByRole('button', { name: 'Сохранить мои слова' });

  // The backend of #689 is stubbed: what the device posts is kept, and a request it has
  // been sent is in `status`; one it has not is 404. `checks` counts the questions.
  const stubRequests = async (context, status = 'pending') => {
    const sent = [];
    const checks = [];
    await context.route('**/api/invite-requests', (route) => {
      sent.push(route.request().postDataJSON());
      return route.fulfill({ status: 201, json: { status: 'pending' } });
    });
    await context.route('**/api/invite-requests/*', (route) => {
      const sha256 = route.request().url().split('/').pop();
      checks.push(sha256);
      return sent.some((request) => request.sha256 === sha256)
        ? route.fulfill({ json: { status, links: { provision: '/api/identity/provision' } } })
        : route.fulfill({ status: 404, json: {} });
    });
    return { checks, sent };
  };

  const openForm = async (page) => {
    await syncButton(page).click();
    await keepWords(page).click();
  };

  const sendNote = async (page, note) => {
    await syncDialog(page).getByRole('textbox').fill(note);
    await syncDialog(page).getByRole('button', { name: 'ОТПРАВИТЬ' }).click();
  };

  // The answer to the next question about a request the server has.
  const nextAnswer = (page) => page.waitForResponse((response) =>
    /\/api\/invite-requests\/[0-9a-f]{64}$/.test(response.url()) && response.status() === 200);

  test('пользователь без аккаунта открывает меню синхронизации → в нём «Сохранить мои слова», иконка называет себя не включённой', async ({ page }) => {
    await test.step('Дано пользователь без аккаунта на главной', async () => {
      await page.goto('/home');
    });

    await test.step('Тогда иконка называет синхронизацию не включённой', async () => {
      await expect(syncButton(page)).toHaveAccessibleName('Синхронизация не включена');
    });

    await test.step('Когда он нажимает чуть правее иконки', async () => {
      const icon = await syncButton(page).boundingBox();
      await page.mouse.click(icon.x + icon.width + 10, icon.y + icon.height / 2);
    });

    await test.step('Тогда открывается меню, и в нём есть «Сохранить мои слова»', async () => {
      await expect(keepWords(page)).toBeVisible();
    });
  });

  test('пользователь без аккаунта пишет пару тёплых слов и отправляет → форма закрывается, запрос уходит на сервер', async ({ page, context }) => {
    let sent;

    await test.step('Дано у пользователя слово «Haus», и он открывает «Сохранить мои слова»', async () => {
      ({ sent } = await stubRequests(context));
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await openForm(page);
    });

    await test.step('Тогда меню закрыто, поле пустое и без фокуса', async () => {
      await expect(syncDialog(page)).toBeVisible();
      await expect(keepWords(page)).toHaveCount(0);
      await expect(syncDialog(page).getByRole('textbox')).toHaveValue('');
      await expect(syncDialog(page).getByRole('textbox')).not.toBeFocused();
    });

    await test.step('Когда он пишет пару слов и нажимает «ОТПРАВИТЬ»', async () => {
      await sendNote(page, '  Спасибо за приложение!  ');
    });

    await test.step('Тогда форма закрывается, а сервер получает записку, число слов, дни и хэш секрета', async () => {
      await expect(syncDialog(page)).toHaveCount(0);
      await expect.poll(() => sent.length).toBe(1);
      expect(sent[0]).toMatchObject({ days: 0, note: 'Спасибо за приложение!', words: 1 });
      expect(sent[0].sha256).toMatch(/^[0-9a-f]{64}$/);
    });
  });

  test('пользователь пишет в форму одни пробелы → «ОТПРАВИТЬ» выключена', async ({ page }) => {
    await test.step('Дано открыта форма «Чтобы слова не потерялись»', async () => {
      await page.goto('/home');
      await openForm(page);
    });

    await test.step('Тогда пустое поле не отправить', async () => {
      await expect(syncDialog(page).getByRole('button', { name: 'ОТПРАВИТЬ' })).toBeDisabled();
    });

    await test.step('Когда он пишет одни пробелы', async () => {
      await syncDialog(page).getByRole('textbox').fill('   ');
    });

    await test.step('Тогда «ОТПРАВИТЬ» по-прежнему выключена', async () => {
      await expect(syncDialog(page).getByRole('button', { name: 'ОТПРАВИТЬ' })).toBeDisabled();
    });
  });

  test('пользователь нажимает «Отмена» в форме → форма закрывается, меню не возвращается', async ({ page }) => {
    await test.step('Дано открыта форма «Чтобы слова не потерялись»', async () => {
      await page.goto('/home');
      await openForm(page);
    });

    await test.step('Когда он нажимает «Отмена»', async () => {
      await syncDialog(page).getByRole('button', { name: 'Отмена' }).click();
    });

    await test.step('Тогда нет ни формы, ни меню', async () => {
      await expect(syncDialog(page)).toHaveCount(0);
      await expect(keepWords(page)).toHaveCount(0);
    });
  });

  test('сервер одобряет запрос, пока приложение открыто → за минуту иконка зеленеет без перезагрузки, в меню «Подключить устройство»', async ({ page, context }) => {
    await test.step('Дано пользователь отправил запрос, и сервер его одобрил', async () => {
      // The page's clock, so the minute between two checks of the request passes at once.
      await page.clock.install();
      await stubRequests(context, 'granted');
      await context.route('**/api/identity/provision', (route) =>
        route.fulfill({ json: { id: 7, token: 'granted-token' } }));
      await stubAccountCopy(context);
      await page.goto('/home');
      await openForm(page);
      const taken = page.waitForResponse((response) =>
        response.url().endsWith('/api/invite-requests') && response.request().method() === 'POST');
      await sendNote(page, 'Спасибо!');
      await taken;
      await expect(syncButton(page)).toHaveCSS('color', GREY);
    });

    await test.step('Когда проходит минута', async () => {
      await page.clock.fastForward('01:05');
    });

    await test.step('Тогда иконка зелёная, а в меню «Подключить устройство»', async () => {
      await expect(syncButton(page)).toHaveCSS('color', GREEN);
      await syncButton(page).click();
      await expect(page.getByRole('button', { name: 'Подключить устройство' })).toBeVisible();
    });
  });

  test('сервер отклоняет запрос, пользователь просит снова → запрос уходит под новым хэшем', async ({ page, context }) => {
    let sent;

    await test.step('Дано пользователь отправил запрос, и сервер его отклонил', async () => {
      await page.clock.install();
      ({ sent } = await stubRequests(context, 'declined'));
      await page.goto('/home');
      await openForm(page);
      await sendNote(page, 'Спасибо!');
      await expect.poll(() => sent.length).toBe(1);
      const declined = nextAnswer(page);
      await page.clock.fastForward('01:05');
      await declined;
    });

    await test.step('Когда он снова пишет и отправляет', async () => {
      await openForm(page);
      await sendNote(page, 'Попробую ещё раз');
    });

    await test.step('Тогда сервер получает новый запрос под другим хэшем', async () => {
      await expect.poll(() => sent.length).toBe(2);
      expect(sent[1]).toMatchObject({ note: 'Попробую ещё раз' });
      expect(sent[1].sha256).not.toBe(sent[0].sha256);
    });
  });

  test('пользователь без аккаунта, который ничего не просил, держит приложение открытым → сервер не спрашивают', async ({ page, context }) => {
    let checks;

    await test.step('Дано пользователь без аккаунта не отправлял форму', async () => {
      await page.clock.install();
      ({ checks } = await stubRequests(context));
      await page.goto('/home');
      await expect(syncButton(page)).toBeVisible();
    });

    await test.step('Когда проходит пять минут', async () => {
      await page.clock.fastForward('05:00');
    });

    await test.step('Тогда сервер о запросе ни разу не спросили', async () => {
      expect(checks).toEqual([]);
    });
  });

  test('пользователь отправляет форму трижды, пока запрос ждёт ответа → о запросе спрашивают раз в минуту, а не три', async ({ page, context }) => {
    let checks;
    let sent;

    await test.step('Дано пользователь отправил запрос', async () => {
      await page.clock.install();
      ({ checks, sent } = await stubRequests(context));
      await page.goto('/home');
      await openForm(page);
      await sendNote(page, 'Привет');
      await expect.poll(() => sent.length).toBe(1);
    });

    await test.step('Когда он отправляет форму ещё дважды и проходит минута', async () => {
      for (const note of ['Привет ещё раз', 'И ещё']) {
        await openForm(page);
        await sendNote(page, note);
        await expect(syncDialog(page)).toHaveCount(0);
      }
      const answered = nextAnswer(page);
      await page.clock.fastForward('01:05');
      await answered;
    });

    await test.step('Тогда запрос ушёл на сервер один раз, и о нём спросили дважды: до отправки и через минуту', async () => {
      expect(sent.length).toBe(1);
      expect(checks.length).toBe(2);
    });
  });
});
