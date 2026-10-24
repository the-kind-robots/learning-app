// Every view transition the page starts, as the kind (its type) it ran under and the
// animations it drew: `<pseudo-element> <keyframes>`, sorted. The browser's
// own (`-ua-…`, the groups' size and position) are left out.
// Installed before the page loads, so the first transition is seen too.
async function recordTransitions(page) {
  await page.addInitScript(() => {
    window.__transitions = [];
    const start = Document.prototype.startViewTransition;
    if (!start) return;
    Document.prototype.startViewTransition = function (...args) {
      const transition = start.apply(this, args);
      const [kind] = transition.types ? [...transition.types] : [];
      transition.ready.then(
        () => window.__transitions.push({
          kind,
          animations: document.getAnimations()
            .filter((a) => a.effect?.pseudoElement?.startsWith('::view-transition') && !a.animationName.startsWith('-ua-'))
            .map((a) => `${a.effect.pseudoElement} ${a.animationName}`)
            .sort(),
        }),
        () => window.__transitions.push({ kind, skipped: true }));
      return transition;
    };
  });
}

const transitions = (page) => page.evaluate(() => window.__transitions);

const forgetTransitions = (page) => page.evaluate(() => { window.__transitions = []; });

// A screen change on a phone takes the page's input until it ends: Chrome
// hit-tests only the document element while one runs, so a touch lands on
// nothing. A spec that touches a screen it just opened waits for this first.
const screenSettled = (page) =>
  page.waitForFunction(() => !document.documentElement.matches(':active-view-transition'));

module.exports = { recordTransitions, transitions, forgetTransitions, screenSettled };
