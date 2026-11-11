// Writes resources/public/link-preview/<language>.png: the picture a messenger shows for a link to the app (#818),
// one 1200×630 PNG per interface language.
// Run from the repository root: node scripts/link-preview.mjs
//
// Each picture: «Sprecha», a line saying what the app is, sprecha.de, and two phones with the statistics screen by
// day and by night. The phones' headers, with their stars, are screenshots in scripts/link-preview/: the statistics
// screen of a development build at eight lessons today, 390 px wide at 3x, the build's D, stamp and trace export
// hidden as a release has none, the sync lit green as for a learner online with an account, cropped to the top 210 px; `header-ru-*` in Russian, «Шпреха», `header-*` in
// English, «Sprecha». The rest of each screen is drawn here: its colours from the app's tokens and formulas, its words
// from the dictionaries, the month and the weekdays from Intl, the legend's groups summed from the bins as
// `domain.statistics/word-groups` sums them. The histogram is the owner's, fuller than the app's.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const LANGUAGES = ['ru', 'en', 'fr', 'tr'];

// A learner of about 5000 words, most of them learned: words by how well they are remembered, forgotten first.
const BINS = [86, 74, 92, 118, 156, 230, 340, 560, 1040, 2210];
// Lessons on each of the last two weeks' days, today last, as many as the headers' stars: some days many, some few.
const LESSONS = [3, 1, 5, 2, 4, 1, 6, 3, 2, 5, 1, 4, 7, 8];
const TODAY = new Date(2026, 10, 11);
const LESSONS_PASSED = 312;

// The line under the name, two lines each.
const LINES = {
  ru: ['Ещё одно приложение', 'для запоминания слов и фраз'],
  en: ['Another app for memorizing', 'words and phrases'],
  fr: ['Encore une appli pour mémoriser', 'des mots et des phrases'],
  tr: ['Kelime ve ifade ezberlemek için', 'bir uygulama daha'],
};

const file = (path) => readFileSync(new URL(`../${path}`, import.meta.url));
const dataUri = (path, type) => `data:${type};base64,${file(path).toString('base64')}`;

// The app's colour tokens as a stylesheet declares them, `--name: value;`.
const tokens = (path) => [...file(path).toString('utf8').matchAll(/(--(?:color-[a-z0-9-]+|soft-fill-opacity)):\s*([^;]+);/g)]
  .map(([, name, value]) => `${name}: ${value};`)
  .join(' ');
const DAY = tokens('resources/public/css/base/colors.css');
// The night redefines some of the day's tokens, as night.css does over colors.css.
const NIGHT = `${DAY} ${tokens('resources/public/css/base/night.css')}`;

const colour = (token) => `rgb(var(--color-${token}))`;
// The memory scale at `percent`: `retention-colour/at`.
const retention = (percent) => `color-mix(in hsl, ${colour('thallium')} ${percent}%, ${colour('neon')})`;
// A soft fill, as the app lays one under a count: its colour at the scheme's soft opacity.
const soft = (fill) => `<span style="position: absolute; inset: 0; border-radius: inherit; background: ${fill}; opacity: var(--soft-fill-opacity)"></span>`;

// A dictionary text: the string of `key` in `language`'s dictionary, `{{:count}}` filled in.
const text = (language, key, count) => {
  const edn = file(`src/shared/i18n/${language}.edn`).toString('utf8');
  const [, value] = edn.match(new RegExp(`${key.replace('/', '\\/')}\\s+(?:\\[:fn/str\\s+)?"([^"]*)"`));
  return value.replace('{{:count}}', count);
};

const sum = (from, to) => BINS.slice(from, to).reduce((a, b) => a + b, 0);
// new, due, good, excellent — `domain.statistics/word-groups`, each in the colour of its middle, as the app's strip.
const GROUPS = [[sum(0, 2), 10, 'new'], [sum(2, 5), 35, 'due'], [sum(5, 8), 65, 'good'], [sum(8, 10), 90, 'excellent']];

const DAYS = LESSONS.map((lessons, i) => {
  const date = new Date(TODAY);
  date.setDate(TODAY.getDate() - (LESSONS.length - 1 - i));
  return { date, lessons, today: i === LESSONS.length - 1 };
});
// The days by month, in the order they come.
const MONTHS = [...DAYS.reduce((months, day) => {
  const key = `${day.date.getFullYear()}-${day.date.getMonth()}`;
  return months.set(key, [...(months.get(key) ?? []), day]);
}, new Map()).values()];

function screen(language) {
  const top = Math.max(...BINS);
  const bars = BINS.map((count, i) => `
    <div style="position: relative; flex: 1; height: ${Math.round(30 + 170 * count / top)}px; border-radius: 10px; display: flex; justify-content: center; padding-top: 6px; box-sizing: border-box">
      ${soft(retention(5 + 10 * i))}
      <span style="position: relative; font-size: 11px; font-weight: 800; color: rgb(48, 48, 48); letter-spacing: -0.03em">${count}</span>
    </div>`).join('');
  const total = GROUPS.reduce((all, [count]) => all + count, 0);
  const strip = GROUPS.map(([count, middle]) => `<div style="flex: ${count / total}; min-width: 16px; height: 11px; border-radius: 6px; background: ${retention(middle)}"></div>`).join('');
  const legend = GROUPS.map(([count, middle, name]) => `
    <div style="display: flex; align-items: center; gap: 7px; white-space: nowrap">
      <span style="width: 11px; height: 11px; border-radius: 50%; background: ${retention(middle)}; flex: none"></span>
      <span style="font-size: 17px; font-weight: 800; color: ${colour('black-text')}">${count}</span>
      <span style="font-size: 13px; font-weight: 600; color: ${colour('quiet-text')}">${text(language, `:retention/${name}`)}</span>
    </div>`).join('');
  const most = Math.max(...LESSONS);
  const monthName = new Intl.DateTimeFormat(language, { month: 'long' });
  const weekday = new Intl.DateTimeFormat(language, { weekday: 'short' });
  const day = ({ date, lessons, today }) => `
    <div style="width: 20px; display: flex; flex-direction: column; align-items: center; gap: 3px">
      <div style="height: 100px; width: calc(100% + 4px); box-sizing: border-box; border-bottom: 2px solid ${colour('swan')}; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; gap: 3px">
        <span style="font-size: 11px; font-weight: 800; color: ${colour('quiet-text')}">${lessons}</span>
        <div style="position: relative; width: 20px; height: ${Math.round(12 + 76 * lessons / most)}px; border-radius: 4px 4px 0 0">${soft(lessons >= 5 ? colour('mystic') : colour('star'))}</div>
      </div>
      <span style="min-width: 18px; text-align: center; border-radius: 9px; padding: 1px 0; font-size: 10.5px; font-weight: ${today ? 800 : 700}; color: ${today ? colour('snow') : colour('quiet-text')}; background: ${today ? colour('black-text') : 'transparent'}">${date.getDate()}</span>
      <span style="font-size: 9px; font-weight: 600; color: ${[0, 6].includes(date.getDay()) ? colour('wolf') : colour('eel')}">${weekday.format(date)}</span>
    </div>`;
  const months = MONTHS.map((days) => `
    <div style="display: flex; flex-direction: column; flex: none">
      <span style="align-self: flex-start; margin-bottom: 6px; padding: 2px 10px; border-radius: 999px; background: ${colour('polar')}; font-size: 11px; font-weight: 800; color: ${colour('eel')}">${monthName.format(days[0].date)}</span>
      <div style="display: flex; gap: 4px">${days.map(day).join('')}</div>
    </div>`).join('');
  return `
    <div style="padding: 10px 20px 0; color: ${colour('black-text')}">
      <div style="font-size: 24px; font-weight: 900; margin-bottom: 14px">${text(language, ':stats/words', total)}</div>
      <div style="display: flex; align-items: flex-end; gap: 6px; height: 200px">${bars}</div>
      <div style="display: flex; gap: 6px; margin-top: 14px">${strip}</div>
      <div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); row-gap: 10px; column-gap: 8px; margin-top: 16px">${legend}</div>
      <div style="font-size: 24px; font-weight: 900; margin-top: 26px">${text(language, ':stats/lessons', LESSONS_PASSED)}</div>
      <div style="display: flex; gap: 4px; margin-top: 10px">${months}</div>
    </div>`;
}

// The share button in the screen's footer, at its foot, as the app's green big button.
const footer = (language) => `
  <div style="position: absolute; left: 0; right: 0; bottom: 0; padding: 16px; background: ${colour('polar')}">
    <div style="height: 50px; box-sizing: border-box; border-radius: 16px; border-bottom: 4px solid ${colour('tree-frog')}; background: ${colour('owl')}; display: flex; align-items: center; justify-content: center; font-size: 17px; font-weight: 700; color: ${colour('snow-always-light')}">${text(language, ':stats/share')}</div>
  </div>`;

// A whole phone at `left`, inside the picture: no edge cuts it, no other phone covers it. Its screen is 390 px wide,
// drawn smaller.
const phone = (scheme, language, left) => `
  <div class="${scheme}" style="position: absolute; left: ${left}px; top: 34px; width: 286px; height: 562px; border-radius: 38px; overflow: hidden; background: ${colour('snow')}; box-shadow: 0 0 0 10px #2b2236, 0 16px 40px rgba(43,34,54,0.22)">
    <div style="position: relative; width: 390px; height: ${562 * 390 / 286}px; transform: scale(${286 / 390}); transform-origin: 0 0; padding-top: 6px; box-sizing: border-box">
      <img src="${dataUri(`scripts/link-preview/header${language === 'ru' ? '-ru' : ''}-${scheme}.png`, 'image/png')}" style="width: 390px; height: 70px; display: block">
      ${screen(language)}
      ${footer(language)}
    </div>
  </div>`;

const font = (name) => dataUri(`resources/public/fonts/Nunito/${name}.woff2`, 'font/woff2');

const picture = (language) => `<!doctype html><html lang="${language}"><head><style>
  @font-face { font-family: Nunito; src: url(${font('nunito-v32-latin-variable')}) format('woff2'); font-weight: 200 1000; }
  @font-face { font-family: Nunito; src: url(${font('nunito-v32-cyrillic-variable')}) format('woff2'); font-weight: 200 1000; unicode-range: U+0400-045F, U+0490-0491, U+04B0-04B1, U+2116; }
  html, body { margin: 0; width: 1200px; height: 630px; overflow: hidden; font-family: Nunito, sans-serif; }
  .light { ${DAY} }
  .dark { ${NIGHT} }
</style></head><body>
  <div style="position: relative; width: 1200px; height: 630px; background: #eef6e6; overflow: hidden">
    <div style="position: absolute; left: 64px; top: 0; bottom: 0; width: 460px; display: flex; flex-direction: column; justify-content: center; gap: 14px; color: #26331c">
      <div style="font-size: 104px; font-weight: 900; line-height: 1; letter-spacing: -0.01em">Sprecha</div>
      <div style="font-size: 28px; font-weight: 700; line-height: 1.25; color: #56634a">${LINES[language].join('<br>')}</div>
      <div style="margin-top: 8px; font-size: 26px; font-weight: 800; color: #58a700">sprecha.de</div>
    </div>
    ${phone('light', language, 540)}
    ${phone('dark', language, 864)}
  </div>
</body></html>`;

// Grey anti-aliasing, not the host's sub-pixel colours, so another machine draws the same bytes.
const browser = await chromium.launch({ channel: 'chrome', headless: true, args: ['--disable-lcd-text'] });
const tab = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 });
for (const language of LANGUAGES) {
  await tab.setContent(picture(language));
  await tab.evaluate(() => document.fonts.ready);
  const path = `resources/public/link-preview/${language}.png`;
  await tab.screenshot({ path: fileURLToPath(new URL(`../${path}`, import.meta.url)) });
  console.log(path);
}
await browser.close();
