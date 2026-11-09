const { test, expect } = require('@playwright/test');

// The server-rendered splash follows the system colour scheme and holds still
// under reduced motion (#413). The app script is blocked, so the splash is
// never replaced and stays on screen for as long as the test reads it.
//
// The splash has no role and no label — it is decoration the app replaces —
// so it is found by its class, the one locator it offers.

async function openSplash(page) {
  await page.route('**/js/app/main.js', (route) => route.abort());
  await page.goto('/');
  await expect(page.locator('.splash')).toBeVisible();
}

const splashLook = (page) => page.evaluate(() => {
  const splash = document.querySelector('.splash');
  const durations = [...document.querySelectorAll('.splash .dot')]
    .map((dot) => dot.getAnimations().map((a) => a.effect.getTiming().duration))
    .flat();
  return {
    ground: getComputedStyle(splash).backgroundColor,
    foliage: getComputedStyle(document.querySelector('.splash-night')).display,
    dotDurations: [...new Set(durations)],
    running: document.getAnimations().length,
  };
});

test('пользователь со светлой темой открывает приложение → точки ü пульсируют на белом в одном ритме', async ({ page }) => {
  await test.step('Дано светлая тема телефона', async () => {
    await page.emulateMedia({ colorScheme: 'light' });
  });

  await test.step('Когда он открывает приложение', async () => {
    await openSplash(page);
  });

  await test.step('Тогда заставка белая, без листвы, у всех точек один ритм', async () => {
    const look = await splashLook(page);
    expect(look.ground).toBe('rgb(255, 255, 255)');
    expect(look.foliage).toBe('none');
    expect(look.dotDurations).toEqual([1400]);
  });
});

test('пользователь с тёмной темой открывает приложение → светлячки мерцают вразнобой на ночном фоне', async ({ page }) => {
  await test.step('Дано тёмная тема телефона', async () => {
    await page.emulateMedia({ colorScheme: 'dark' });
  });

  await test.step('Когда он открывает приложение', async () => {
    await openSplash(page);
  });

  await test.step('Тогда заставка на ночном фоне, с листвой, точки мерцают каждая в своём ритме', async () => {
    const look = await splashLook(page);
    expect(look.ground).toBe('rgb(21, 15, 28)');
    expect(look.foliage).toBe('block');
    expect(look.dotDurations.length).toBeGreaterThan(1);
  });
});

test('пользователь просит меньше движения → на заставке ничего не движется ни в светлой, ни в тёмной теме', async ({ page }) => {
  for (const colorScheme of ['light', 'dark']) {
    await test.step(`Дано ${colorScheme === 'light' ? 'светлая' : 'тёмная'} тема и просьба о меньшем движении`, async () => {
      await page.emulateMedia({ colorScheme, reducedMotion: 'reduce' });
    });

    await test.step('Когда он открывает приложение', async () => {
      await openSplash(page);
    });

    await test.step('Тогда на заставке нет ни одной анимации', async () => {
      expect((await splashLook(page)).running).toBe(0);
    });
  }
});
