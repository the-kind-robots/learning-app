const { test, expect } = require('./fixtures');

// The guard in fixtures.js must fail a test that renders while a render is in
// progress. This test makes one on purpose, in an element of its own outside
// the app's body: a mount hook that renders again.
//
// It is expected to fail, and only through the guard. The test body passes
// when Replicant's report reaches the console; the guard then fails the test
// as it tears down. Were the report not to arrive, the test is not marked as
// expected to fail and fails for real, so a silent Replicant cannot make it
// pass.

const NESTED_RENDER = 'Triggered a render while rendering';

test('the guard fails a test that renders during a render', async ({ page }) => {
  await page.goto('/home');
  await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();

  const reported = page
    .waitForEvent('console', { predicate: (m) => m.text().includes(NESTED_RENDER), timeout: 5000 })
    .then(() => true, () => false);
  await page.evaluate(() => {
    const kw = cljs.core.keyword;
    const vec = (...xs) => cljs.core.PersistentVector.fromArray(xs, true);
    const el = document.createElement('div');
    document.documentElement.appendChild(el);
    const renderAgain = () => replicant.dom.render(el, vec(kw('span'), 'again'));
    const attrs = cljs.core.PersistentArrayMap.fromArray([kw('replicant', 'on-mount'), renderAgain], true);
    replicant.dom.render(el, vec(kw('div'), attrs, 'first'));
  });

  const arrived = await reported;
  test.fail(arrived, 'the guard is expected to fail this test');
  expect(arrived, 'Replicant reported the nested render').toBe(true);
});
