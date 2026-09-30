const { test, expect } = require('./fixtures');

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

test('between the server splash and home nothing else is shown', async ({ page }) => {
  await page.addInitScript(watchStartup);
  await page.goto('/home');
  await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();

  expect(await page.evaluate(() => window.__shown)).toEqual(['splash', 'home']);
});
