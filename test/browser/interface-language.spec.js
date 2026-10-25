const { test, expect } = require('./fixtures');

// The interface's text comes from the dictionary of one language, and the page
// says which: a screen reader picks its voice and a browser its hyphenation by
// `<html lang>` (#472). The learner picks the language in the settings, and the
// pick lives in user-db, so it follows them to every device (#473). A language
// is there when its `i18n/<language>.edn` is: this build carries ru, en, tr and
// fr. The settings are named in the language on screen.

const home = (page) => page.getByRole('heading', { name: /^(Главная|Home|Accueil|Ana sayfa)$/ });
const settings = (page) => page.getByRole('dialog', { name: /^(Настройки|Settings|Paramètres|Ayarlar)$/ });
const language = (page, name) => settings(page).getByRole('radio', { name });
const wordMark = (page) => page.getByRole('button', { name: /^Sprecha/ });

const storeSettings = (page, doc) =>
  page.evaluate((doc) => db.use('user-db').put({ _id: 'settings', type: 'settings', ...doc }), doc);

test.describe('Язык интерфейса', () => {
  test('пользователь открывает приложение → страница объявлена русской, главная читается по-русски', async ({ page }) => {
    await test.step('Когда он открывает приложение', async () => {
      await page.goto('/');
    });

    await test.step('Тогда страница объявлена русской, а главная говорит по-русски', async () => {
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
      await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
      await expect(page.getByLabel('Слово (немецкий)')).toHaveAttribute('placeholder', 'Новое слово');
    });
  });

  test('браузер просит страницу по-английски или по-японски → сервер объявляет её английской', async ({ request }) => {
    for (const tag of ['en-GB,en;q=0.9', 'ja-JP']) {
      await test.step(`Когда браузер просит страницу с Accept-Language: ${tag}`, async () => {
        const html = await (await request.get('/', { headers: { 'Accept-Language': tag } })).text();
        expect(html).toContain('<html lang="en">');
      });
    }
  });

  test('пользователь нажимает «Sprecha» → в настройках четыре языка своими названиями, Русский отмечен', async ({ page }) => {
    await test.step('Дано открытая главная', async () => {
      await page.goto('/');
      await expect(home(page)).toBeVisible();
    });

    await test.step('Когда он нажимает «Sprecha»', async () => {
      await wordMark(page).click();
    });

    await test.step('Тогда в группе «Язык» English, Français, Русский и Türkçe, и отмечен Русский', async () => {
      await expect(settings(page).getByRole('group', { name: 'Язык' }).getByRole('radio'))
        .toHaveCount(4);
      for (const name of ['English', 'Français', 'Türkçe']) {
        await expect(language(page, name)).not.toBeChecked();
      }
      await expect(language(page, 'Русский')).toBeChecked();
    });
  });

  test('пользователь выбирает English → интерфейс английский сразу и после перезагрузки', async ({ page }) => {
    await test.step('Дано открытые настройки', async () => {
      await page.goto('/');
      await wordMark(page).click();
    });

    await test.step('Когда он выбирает English', async () => {
      await language(page, 'English').click();
    });

    await test.step('Тогда English отмечен, главная называется «Home», страница английская', async () => {
      await expect(language(page, 'English')).toBeChecked();
      await expect(language(page, 'Русский')).not.toBeChecked();
      await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    });

    await test.step('Когда он перезагружает страницу', async () => {
      await page.reload();
    });

    await test.step('Тогда главная по-прежнему английская', async () => {
      await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    });
  });

  test('пользователь нажимает мимо настроек или Esc → настройки закрыты, главная на месте', async ({ page }) => {
    await test.step('Дано открытые настройки', async () => {
      await page.goto('/');
      await wordMark(page).click();
      await expect(settings(page)).toBeVisible();
    });

    await test.step('Когда он нажимает в низ экрана, мимо настроек', async () => {
      await page.mouse.click(640, 650);
    });

    await test.step('Тогда настроек нет, а главная видна', async () => {
      await expect(settings(page)).toHaveCount(0);
      await expect(home(page)).toBeVisible();
    });

    await test.step('Когда он снова открывает настройки и нажимает Esc', async () => {
      await wordMark(page).click();
      await expect(settings(page)).toBeVisible();
      await page.keyboard.press('Escape');
    });

    await test.step('Тогда настроек снова нет', async () => {
      await expect(settings(page)).toHaveCount(0);
    });
  });

  test('пользователь нажимает «Sprecha» при открытых настройках → настройки закрываются', async ({ page }) => {
    await test.step('Дано открытые настройки', async () => {
      await page.goto('/');
      await wordMark(page).click();
      await expect(settings(page)).toBeVisible();
    });

    await test.step('Когда он снова нажимает «Sprecha»', async () => {
      await wordMark(page).click();
    });

    await test.step('Тогда настроек нет', async () => {
      await expect(settings(page)).toHaveCount(0);
    });
  });

  test('выбор языка приходит с другого устройства → интерфейс переключается на него', async ({ page }) => {
    await test.step('Дано открытая русская главная', async () => {
      await page.goto('/');
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
    });

    await test.step('Когда в user-db приходит документ настроек с French, как его приносит синхронизация', async () => {
      await storeSettings(page, { language: 'fr' });
    });

    await test.step('Тогда главная называется «Accueil», страница французская', async () => {
      await expect(page.getByRole('heading', { name: 'Accueil' })).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
    });
  });

  test('с другого устройства приходит язык, которого в этой сборке нет → интерфейс по системе, выбор не тронут', async ({ page }) => {
    await test.step('Дано в user-db документ настроек с языком «es»', async () => {
      await page.goto('/');
      await expect(home(page)).toBeVisible();
      await storeSettings(page, { language: 'es' });
      await page.reload();
    });

    await test.step('Тогда интерфейс русский, как система, и в настройках отмечен Русский', async () => {
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
      await wordMark(page).click();
      await expect(language(page, 'Русский')).toBeChecked();
    });

    await test.step('И документ по-прежнему держит «es»', async () => {
      expect(await page.evaluate(() => db.use('user-db').get('settings').then((doc) => doc.language)))
        .toBe('es');
    });
  });

  test.describe('на турецкой системе', () => {
    test.use({ locale: 'tr-TR' });

    test('пользователь без выбора открывает приложение → интерфейс турецкий, настройки не открыты', async ({ page }) => {
      await test.step('Когда он открывает приложение', async () => {
        await page.goto('/');
      });

      await test.step('Тогда главная называется «Ana sayfa», страница турецкая, настроек нет', async () => {
        await expect(page.getByRole('heading', { name: 'Ana sayfa' })).toBeVisible();
        await expect(page.locator('html')).toHaveAttribute('lang', 'tr');
        await expect(settings(page)).toHaveCount(0);
      });
    });
  });

  test.describe('на японской системе', () => {
    test.use({ locale: 'ja-JP' });

    test('пользователь без выбора открывает приложение → интерфейс английский', async ({ page }) => {
      await test.step('Когда он открывает приложение', async () => {
        await page.goto('/');
      });

      await test.step('Тогда главная называется «Home», страница английская', async () => {
        await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();
        await expect(page.locator('html')).toHaveAttribute('lang', 'en');
      });
    });
  });

  test.describe('на немецкой системе', () => {
    test.use({ locale: 'de-DE' });

    test('пользователь без выбора открывает приложение → настройки открыты с English в фокусе, закрыть их может только выбор', async ({ page }) => {
      await test.step('Когда он открывает приложение', async () => {
        await page.goto('/');
      });

      await test.step('Тогда открыты настройки, English отмечен и в фокусе', async () => {
        await expect(settings(page)).toBeVisible();
        await expect(language(page, 'English')).toBeChecked();
        await expect(language(page, 'English')).toBeFocused();
      });

      await test.step('Когда он нажимает Esc и мимо настроек', async () => {
        await page.keyboard.press('Escape');
        await page.mouse.click(640, 650);
      });

      await test.step('Тогда настройки по-прежнему открыты', async () => {
        await expect(settings(page)).toBeVisible();
      });

      await test.step('Когда он выбирает Français', async () => {
        await language(page, 'Français').click();
      });

      await test.step('Тогда настройки закрыты, главная французская', async () => {
        await expect(settings(page)).toHaveCount(0);
        await expect(page.getByRole('heading', { name: 'Accueil' })).toBeVisible();
      });

      await test.step('Когда он перезагружает страницу', async () => {
        await page.reload();
      });

      await test.step('Тогда настройки не открываются, главная французская', async () => {
        await expect(page.getByRole('heading', { name: 'Accueil' })).toBeVisible();
        await expect(settings(page)).toHaveCount(0);
      });
    });

    test('пользователь оставляет предложенный English → настройки закрыты, English сохранён', async ({ page }) => {
      await test.step('Дано открытые при старте настройки с отмеченным English', async () => {
        await page.goto('/');
        await expect(language(page, 'English')).toBeChecked();
      });

      await test.step('Когда он нажимает уже отмеченный English', async () => {
        await language(page, 'English').click();
      });

      await test.step('Тогда настройки закрыты, а после перезагрузки не открываются и главная английская', async () => {
        await expect(settings(page)).toHaveCount(0);
        await page.reload();
        await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();
        await expect(settings(page)).toHaveCount(0);
      });
    });

    test('с другого устройства пришёл язык, которого в этой сборке нет → настройки не открываются', async ({ page }) => {
      await test.step('Дано в user-db документ настроек с языком «es»', async () => {
        await page.goto('/');
        await expect(settings(page)).toBeVisible();
        await storeSettings(page, { language: 'es' });
        await page.reload();
      });

      await test.step('Тогда главная английская, а настройки не открыты', async () => {
        await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();
        await expect(settings(page)).toHaveCount(0);
      });
    });
  });
});
