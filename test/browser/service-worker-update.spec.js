const { test, expect, controlled, nothingHappensFor } = require('./fixtures');
const shared = require('./service-worker.shared');

// The worker's update path (ADR-0022). The backend prepends SW_VERSION to
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

// The worker's build buckets, by their prefix; the page keeps caches of its
// own beside them.
async function cacheBuckets(page) {
  return page.evaluate(async () => (await caches.keys()).filter((key) => key.startsWith('shell-')));
}

// A development build has the metrics globals; a release build has none.
async function developmentBuild(page) {
  return page.evaluate(() => typeof window.__metrics === 'function');
}

// A new build arriving: the check on return to the app fetches the real sw.js,
// which differs from test-v1, and it installs. Resolves once it waits. The
// install precaches the whole shell, which took longer than the default 5 s
// with the suite running in parallel.
async function newBuildWaiting(page) {
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
  await expect.poll(() => page.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration();
    return registration.waiting && registration.waiting.state;
  }), { timeout: 30000 }).toBe('installed');
}

// DevTools' «Update on reload» (#517): every load of a controlled page makes
// the browser install a worker once more and put it in charge at once, waiting
// skipped. The flag belongs to the storage partition, so one CDP session sets
// it for every page of the context.
async function updateOnReload(context, page) {
  const cdp = await context.newCDPSession(page);
  await cdp.send('ServiceWorker.enable');
  await cdp.send('ServiceWorker.setForceUpdateOnPageLoad', { forceUpdateOnPageLoad: true });
}

// Controller changes as the current document saw them, and whether it has
// started to reload: `beforeunload` fires when the reload is asked for, in the
// document that asks, long before the next one loads. The listeners come
// before the app's, and the app has nothing that waits before reloading, so
// by the time a change is counted a reload it caused would be marked.
const markReloads = (page) => page.addInitScript(() => {
  window.__controllerChanges = 0;
  window.__reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => { window.__controllerChanges++; });
  addEventListener('beforeunload', () => { window.__reloading = true; });
});

// The page saw its controller change to a worker that is now activated, and
// did not start to reload for it.
async function stayedThroughTakeover(page) {
  await page.waitForFunction(() => window.__controllerChanges >= 1 &&
                                   navigator.serviceWorker.controller.state === 'activated');
  expect(await page.evaluate(() => window.__reloading)).toBe(false);
}

const updateControl = (page) => page.getByRole('button', { name: 'Обновить' });

// The build mark exists in a development build only; on a release build the
// tests that tap it skip.
const buildMark = (page) => page.getByRole('button', { name: 'Перезагрузить сборку' });

test.describe('Обновление приложения', () => {
  test('новая версия вышла, пока открыты страницы → ни одна не предлагает обновиться и не перезагружается, даже когда DevTools принудительно включает версию', async ({ context }) => {
    const pages = [];

    await test.step('Дано три страницы приложения на старой версии', async () => {
      await registerWorkerVersion(context, 'test-v1');
      for (let i = 0; i < 3; i++) {
        const page = await context.newPage();
        await markReloads(page);
        await openControlled(page);
        pages.push(page);
      }
      // From here the registration's own fetch brings the real build too, as a
      // deployed one does: test-v1 was the build before it.
      await context.unrouteAll();
    });

    const [first, ...others] = pages;

    await test.step('Когда новая версия установилась и ждёт', async () => {
      await newBuildWaiting(first);
    });

    await test.step('Тогда кнопки «Обновить» нет ни на одной странице', async () => {
      for (const page of pages) await expect(updateControl(page)).toHaveCount(0);
    });

    await test.step('Когда DevTools принудительно включает версию под открытыми страницами', async () => {
      await updateOnReload(context, first);
      await first.reload();
    });

    await test.step('Тогда контроллер сменился, а страницы не перезагружаются', async () => {
      for (const page of others) {
        await stayedThroughTakeover(page);
        await expect(updateControl(page)).toHaveCount(0);
      }
      expect(await first.evaluate(() => window.__reloading)).toBe(false);
    });
  });

  test('все страницы приложения закрыты, новая версия ждала → следующее открытие запускает её', async ({ context }) => {
    let first;
    let second;
    let next;

    await test.step('Дано страница на старой версии и новая версия, которая ждёт', async () => {
      await registerWorkerVersion(context, 'test-v1');
      first = await context.newPage();
      await openControlled(first);
      await context.unrouteAll();
      await newBuildWaiting(first);
    });

    await test.step('Когда открывается вторая страница', async () => {
      second = await context.newPage();
      await second.goto('/');
    });

    await test.step('Тогда она работает на уже запущенной версии, новая ждёт', async () => {
      expect(await second.evaluate(async () => {
        const registration = await navigator.serviceWorker.getRegistration();
        return navigator.serviceWorker.controller.scriptURL === registration.active.scriptURL &&
               registration.active.state === 'activated' &&
               registration.waiting !== null;
      })).toBe(true);
      expect(await cacheBuckets(second)).toContain('shell-test-v1');
    });

    await test.step('Когда обе страницы закрыты и приложение открыто снова', async () => {
      await first.close();
      await second.close();
      next = await context.newPage();
      await openControlled(next);
    });

    await test.step('Тогда работает новая версия, старой больше нет', async () => {
      await expect.poll(() => cacheBuckets(next)).toHaveLength(1);
      expect(await cacheBuckets(next)).not.toContain('shell-test-v1');
      expect(await next.evaluate(async () =>
        (await navigator.serviceWorker.getRegistration()).waiting)).toBeNull();
    });
  });

  test('включён «Update on reload», страница перезагружена один раз → загрузка одна', async ({ context, page }) => {
    const loads = [];

    await test.step('Дано страница под воркером и включённый «Update on reload»', async () => {
      await openControlled(page);
      await updateOnReload(context, page);
      await markReloads(page);
      page.on('load', () => loads.push(Date.now()));
    });

    await test.step('Когда страница перезагружается', async () => {
      await page.reload();
    });

    await test.step('Тогда та же версия, установленная заново, взяла документ, загрузка одна', async () => {
      await stayedThroughTakeover(page);
      expect(loads.length).toBe(1);
    });
  });

  test('пользователь нажимает на метку сборки → страница перезагружается: на новую версию, если она вышла, иначе на ту же', async ({ context, page }) => {
    let bucket;

    await test.step('Дано приложение на старой версии, вышла новая', async () => {
      await registerWorkerVersion(context, 'test-v1');
      await openControlled(page);
      test.skip(!(await developmentBuild(page)), 'the build mark exists in a development build only');
    });

    await test.step('Когда он нажимает на метку сборки', async () => {
      // The update check behind the tap brings the real build.
      const reloaded = page.waitForEvent('load');
      await buildMark(page).click();
      await reloaded;
    });

    await test.step('Тогда страница перезагружена на новую версию', async () => {
      await controlled(page);
      await expect.poll(async () => (await cacheBuckets(page)).length).toBe(1);
      expect(await cacheBuckets(page)).not.toEqual(['shell-test-v1']);
      expect(await page.evaluate(() => window.__firstLoad)).toBeUndefined();
      [bucket] = await cacheBuckets(page);
    });

    await test.step('Когда новой версии больше нет и он снова нажимает на метку', async () => {
      await page.evaluate(() => { window.__secondLoad = true; });
      const reloaded = page.waitForEvent('load');
      await buildMark(page).click();
      await reloaded;
    });

    await test.step('Тогда страница всё равно перезагружена, версия та же', async () => {
      expect(await page.evaluate(() => window.__secondLoad)).toBeUndefined();
      await expect.poll(() => cacheBuckets(page)).toEqual([bucket]);
    });
  });

  test('воркер не откликается на просьбу занять место, пользователь нажимает на метку сборки → страница перезагружается один раз по таймауту', async ({ context, page }) => {
    const loads = [];

    await test.step('Дано приложение на старой версии, вышла новая, а просьба занять место не доходит', async () => {
      await page.addInitScript(() => {
        ServiceWorker.prototype.postMessage = () => {};
      });
      await registerWorkerVersion(context, 'test-v1');
      await openControlled(page);
      test.skip(!(await developmentBuild(page)), 'the build mark exists in a development build only');
      page.on('load', () => loads.push(Date.now()));
    });

    await test.step('Когда он нажимает на метку сборки', async () => {
      await buildMark(page).click();
    });

    await test.step('Тогда страница перезагружена, и только один раз', async () => {
      await expect.poll(() => loads.length, { timeout: 15000 }).toBe(1);
      await controlled(page);
      expect(await page.evaluate(() => window.__firstLoad)).toBeUndefined();
      await nothingHappensFor(page, 4000);
      expect(loads.length).toBe(1);
    });
  });
});
