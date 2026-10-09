const { test, expect } = require('./fixtures');
const shared = require('./service-worker.shared');

// The worker's update path (ADR-0014). The backend prepends SW_VERSION to
// sw.js from a hash of resources/public, and a build "changes" here by
// handing the registration a script with another version line. Only the
// registration's fetch can be rewritten: it passes through context.route
// (page.route never sees it — the real hash came through, measured), while
// the fetch behind registration.update() bypasses routing altogether and
// brings the backend's real build. That is the changed sw.js. Everything
// else about the worker — precache, waiting, activate, claim — runs for real
// in the browser.
async function registerWorkerVersion(context, version) {
  await context.route('**/js/app/sw.js', async (route) => {
    const response = await route.fetch();
    const body = (await response.text()).replace(/^const SW_VERSION="[^"]*"/, `const SW_VERSION="${version}"`);
    await route.fulfill({ response, body });
  });
}

// The first load, marked before the worker claims it: the mark survives only
// if that claim did not reload the page.
const openControlled = (page) =>
  shared.openControlled(page, (p) => p.evaluate(() => { window.__firstLoad = true; }));

// The worker's states as the page saw them, kept in localStorage because the
// page that records them is about to reload.
async function recordWorkerStates(page) {
  await page.evaluate(async () => {
    localStorage.setItem('sw-states', '[]');
    const registration = await navigator.serviceWorker.getRegistration();
    registration.addEventListener('updatefound', () => {
      const worker = registration.installing;
      worker.addEventListener('statechange', () => {
        const states = JSON.parse(localStorage.getItem('sw-states'));
        states.push(worker.state);
        localStorage.setItem('sw-states', JSON.stringify(states));
      });
    });
  });
}

// The worker's build buckets, by their prefix; the page keeps caches of its
// own beside them.
async function cacheBuckets(page) {
  return page.evaluate(async () => (await caches.keys()).filter((key) => key.startsWith('shell-')));
}

// A development build has the metrics globals; a release build has none.
async function developmentBuild(page) {
  return page.evaluate(() => typeof window.__metrics === 'function');
}

test.describe('Обновление приложения', () => {
  test('пользователь возвращается в приложение после выхода новой версии → кнопка «Обновить» появляется, а страница перезагружается один раз после нажатия', async ({ context, page }) => {
    const loads = [];
    const update = page.getByRole('button', { name: 'Обновить' });
    let dev;
    let reloaded;
    page.on('load', () => loads.push(Date.now()));

    await test.step('Дано приложение открыто на старой версии, обновления нет', async () => {
      await registerWorkerVersion(context, 'test-v1');
      await openControlled(page);
      // The first worker claimed a page that started uncontrolled: no reload.
      expect(await page.evaluate(() => window.__firstLoad)).toBe(true);
      expect(await cacheBuckets(page)).toEqual(['shell-test-v1']);
      // The snapshot of memory, written once memory is loaded.
      await expect.poll(() => page.evaluate(async () => !!(await (await caches.open('learner-memory')).match('/learner-memory/snapshot')))).toBe(true);
      await recordWorkerStates(page);
      // The update path does not differ between builds; only the check of
      // where memory came from after the reload needs the development build.
      dev = await developmentBuild(page);
      await expect(update).toHaveCount(0);
    });

    await test.step('Когда он возвращается в приложение и выходит новая версия', async () => {
      // Coming back to the app is what asks for the update check; the check
      // brings the real build, which differs from test-v1.
      reloaded = page.waitForEvent('load');
      await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    });

    await test.step('Тогда предлагается «Обновить», страница не перезагружена', async () => {
      await expect(update).toBeVisible();
      expect(loads.length).toBe(1);
    });

    await test.step('Когда он нажимает «Обновить»', async () => {
      await update.click();
      await reloaded;
    });

    await test.step('Тогда страница на новой версии, перезагрузка была одна', async () => {
      await page.waitForFunction(() => navigator.serviceWorker.controller !== null);
      await expect.poll(async () => (await cacheBuckets(page)).length).toBe(1);
      expect(await cacheBuckets(page)).not.toEqual(['shell-test-v1']);
      // The page after the reload started from the snapshot: activation kept it.
      if (dev) {
        await page.waitForFunction(() => typeof window.__metrics === 'function' && window.__metrics().memory['ready-ms']);
        expect(await page.evaluate(() => window.__metrics().memory.from)).toBe('snapshot');
      }
      expect(await page.evaluate(() => JSON.parse(localStorage.getItem('sw-states')))).toEqual(['installed', 'activating', 'activated']);
      expect(await page.evaluate(() => window.__firstLoad)).toBeUndefined();
      // One reload, not two: the bucket assertion above already let a second
      // controller change — had there been one — fire, so this is not a race.
      expect(loads.length).toBe(2);
    });
  });

  // The build mark exists in a development build only; on a release build
  // this skips.
  test('пользователь нажимает на метку сборки → страница перезагружается: на новую версию, если она вышла, иначе на ту же', async ({ context, page }) => {
    const buildMark = page.getByRole('button', { name: 'Перезагрузить сборку' });
    let bucket;

    await test.step('Дано приложение на старой версии, вышла новая', async () => {
      await registerWorkerVersion(context, 'test-v1');
      await openControlled(page);
      test.skip(!(await developmentBuild(page)), 'the build mark exists in a development build only');
    });

    await test.step('Когда он нажимает на метку сборки', async () => {
      // The update check behind the tap brings the real build.
      const reloaded = page.waitForEvent('load');
      await buildMark.click();
      await reloaded;
    });

    await test.step('Тогда страница перезагружена на новую версию', async () => {
      await page.waitForFunction(() => navigator.serviceWorker.controller !== null);
      await expect.poll(async () => (await cacheBuckets(page)).length).toBe(1);
      expect(await cacheBuckets(page)).not.toEqual(['shell-test-v1']);
      expect(await page.evaluate(() => window.__firstLoad)).toBeUndefined();
      [bucket] = await cacheBuckets(page);
    });

    await test.step('Когда новой версии больше нет и он снова нажимает на метку', async () => {
      // The registered build is now the real one; the check finds it unchanged.
      await page.evaluate(() => { window.__secondLoad = true; });
      const reloaded = page.waitForEvent('load');
      await buildMark.click();
      await reloaded;
    });

    await test.step('Тогда страница всё равно перезагружена, версия та же', async () => {
      expect(await page.evaluate(() => window.__secondLoad)).toBeUndefined();
      await expect.poll(() => cacheBuckets(page)).toEqual([bucket]);
    });
  });
});
