// Every view transition the page starts, as the kind (its type) it ran under and the
// animations it drew: `<pseudo-element> <keyframes>`, sorted. The browser's
// own (`-ua-…`, the groups' size and position) are left out.
// Installed before the page loads, so the first transition is seen too.
// The main button's own, the browser's included, are kept apart: what it draws is the browser's cross-fade.
async function recordTransitions(page) {
  await page.addInitScript(() => {
    window.__transitions = [];
    window.__mainButton = [];
    const start = Document.prototype.startViewTransition;
    if (!start) return;
    Document.prototype.startViewTransition = function (...args) {
      const transition = start.apply(this, args);
      const [kind] = transition.types ? [...transition.types] : [];
      transition.ready.then(
        () => {
          const drawn = document.getAnimations().filter((a) => a.effect?.pseudoElement?.startsWith('::view-transition'));
          window.__transitions.push({
            kind,
            animations: drawn
              .filter((a) => !a.animationName.startsWith('-ua-'))
              .map((a) => `${a.effect.pseudoElement} ${a.animationName}`)
              .sort(),
          });
          window.__mainButton.push({
            kind,
            animations: drawn
              .filter((a) => a.effect.pseudoElement.endsWith('(main-button)'))
              .map((a) => `${a.effect.pseudoElement} ${a.animationName}`)
              .sort(),
          });
        },
        () => window.__transitions.push({ kind, skipped: true }));
      return transition;
    };
  });
}

const transitions = (page) => page.evaluate(() => window.__transitions);

// What the main button drew in each transition.
const mainButtonTransitions = (page) => page.evaluate(() => window.__mainButton);

const forgetTransitions = (page) => page.evaluate(() => { window.__transitions = []; window.__mainButton = []; });

// A screen change on a phone takes the page's input until it ends: Chrome
// hit-tests only the document element while one runs, so a touch lands on
// nothing. A spec that touches a screen it just opened waits for this first.
const screenSettled = (page) =>
  page.waitForFunction(() => !document.documentElement.matches(':active-view-transition'));

module.exports = { recordTransitions, transitions, mainButtonTransitions, forgetTransitions, screenSettled };
