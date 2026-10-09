const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// The server's splash stays until the first screen replaces it; nothing is
// shown in between (#515).
//
// An init script watches the document from before the app boots and records
// what the body shows after every batch of changes: the splash, the screen, or
// something else. Mutation callbacks run before the browser paints, so every
// state that could reach the screen is recorded. The observer is attached to
// `document`, because an init script runs before the document has a body.
// Recording starts when the splash is first seen: before that the parser is
// still writing the body, and a slow machine can hand the observer a body
// that holds only the splash's style.

const watchStartup = `
  window.__shown = [];
  new MutationObserver(() => {
    const body = document.body;
    if (!body) return;
    if (window.__shown.length === 0 && !body.querySelector('.splash')) return;
    const shown = body.querySelector('.splash')
      ? 'splash'
      : body.querySelector('h1, h2') && body.textContent.includes('Главная')
        ? 'home'
        : 'other: ' + body.textContent.trim().slice(0, 60);
    if (window.__shown[window.__shown.length - 1] !== shown) window.__shown.push(shown);
  }).observe(document, { childList: true, subtree: true, characterData: true });
`;

// A screen that is on display stays on display when animation frames run late
// (#515).
//
// A tab loaded in the background runs no frames until it is shown, and a busy
// phone runs them late. Replicant defers a render asked for during a render to
// the next frame. When memory loaded before that frame, the screen was drawn,
// and then the frame drew the older hiccup over it — the splash — and nothing
// rendered again.
//
// The init script delays every frame by a second, so memory loads before the
// first frame runs. Frames run in the order they were asked for, and the
// script counts both. Once the screen is on display, the check waits until
// every frame asked for by then has run: a stale render deferred during boot
// is one of them, and whatever it draws is drawn by then. The development
// build asks for a frame in every frame, so the count of frames waiting never
// drops to zero.

const FRAME_DELAY_MS = 1000;

const delayFrames = (delayMs) => {
  const request = window.requestAnimationFrame.bind(window);
  // Declared for the nested-render guard in fixtures.js, which stretches
  // its after-test waits past this delay; without it the guard gives up
  // before a report queued behind the delayed frames can run.
  window.__frameDelayMs = delayMs;
  window.__framesAsked = 0;
  window.__framesRun = 0;
  window.requestAnimationFrame = (callback) => {
    window.__framesAsked++;
    setTimeout(() => request((time) => {
      window.__framesRun++;
      callback(time);
    }), delayMs);
    return 0;
  };
};

// Waits until every frame asked for so far has run: a stale render deferred
// during boot is one of them, and whatever it draws is drawn by then.
async function runAskedFrames(page) {
  const asked = await page.evaluate(() => window.__framesAsked);
  await page.waitForFunction((n) => window.__framesRun >= n, asked, { polling: 100 });
}

test.describe('Старт приложения', () => {
  test('пользователь открывает приложение → заставка сразу сменяется экраном, без промежуточных кадров', async ({ page }) => {
    await test.step('Когда он открывает главную', async () => {
      await page.addInitScript(watchStartup);
      await page.goto('/home');
    });

    await test.step('Тогда между заставкой сервера и главной ничего не показывалось', async () => {
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
      expect(await page.evaluate(() => window.__shown)).toEqual(['splash', 'home']);
    });
  });

  test('пользователь открывает приложение на медленном телефоне, где кадры запаздывают → главная и урок остаются на экране', async ({ page }) => {
    await test.step('Когда кадры запаздывают на секунду и он открывает главную', async () => {
      await page.addInitScript(delayFrames, FRAME_DELAY_MS);
      await page.goto('/home');
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
      await runAskedFrames(page);
    });

    await test.step('Тогда главная остаётся, «Загружаем...» не возвращается', async () => {
      await expect(page.getByText('Загружаем...')).toHaveCount(0);
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
    });

    await test.step('Когда он добавляет слово и открывает урок', async () => {
      await addWord(page, 'der Hund', 'пёс');
      await page.goto('/lesson');
      await expect(page.locator('.lesson__prompt')).toBeVisible();
      await runAskedFrames(page);
    });

    await test.step('Тогда задание урока остаётся, «Загружаем...» не возвращается', async () => {
      await expect(page.getByText('Загружаем...')).toHaveCount(0);
      await expect(page.locator('.lesson__prompt')).toBeVisible();
    });
  });
});
