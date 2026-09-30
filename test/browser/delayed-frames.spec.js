const { test, expect } = require('./fixtures');
const { addWord } = require('./lesson-answer.shared');

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

async function expectScreenStays(page, screen) {
  await expect(screen).toBeVisible();
  const asked = await page.evaluate(() => window.__framesAsked);
  await page.waitForFunction((n) => window.__framesRun >= n, asked, { polling: 100 });
  await expect(page.getByText('Загружаем...')).toHaveCount(0);
  await expect(screen).toBeVisible();
}

test('home stays on display when frames run late', async ({ page }) => {
  await page.addInitScript(delayFrames, FRAME_DELAY_MS);
  await page.goto('/home');
  await expectScreenStays(page, page.getByRole('heading', { name: 'Главная' }));
});

test('the lesson stays on display when frames run late', async ({ page }) => {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');

  await page.addInitScript(delayFrames, FRAME_DELAY_MS);
  await page.goto('/lesson');
  await expectScreenStays(page, page.locator('.lesson__prompt'));
});
