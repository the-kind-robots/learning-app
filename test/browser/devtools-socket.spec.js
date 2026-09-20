const { test, expect } = require('@playwright/test');

// Where the shadow-cljs devtools client will open its socket. The preload
// (`dev/cljs/dev/devtools_socket.cljs`) sets it before the client loads, and a
// development build leaves the namespaces on the global object, so the value
// can be read straight off it.
const devtoolsUrl = (page) =>
  page.evaluate(() => window.shadow.cljs.devtools.client.env.devtools_url);

// One arm, because the namespace has one: the page's own origin, which is what
// every host nginx serves proxies to the watch. There is nothing to override it
// with, so there is no second bundle a spec would have to be built against.
test('the socket follows the page origin', async ({ page, baseURL }) => {
  await page.goto('/');
  await page.waitForFunction(() => window.shadow !== undefined);
  expect(await devtoolsUrl(page)).toBe(`${new URL(baseURL).origin}/shadow-cljs`);
});
