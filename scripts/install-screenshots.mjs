// Writes resources/public/screenshots/<language>/<screen>.png: the screenshots the install offer shows (#820, #826),
// five per interface language, 390×844 at 3x.
// Run from the repository root against a backend that serves the development build (test/browser/README.md,
// Running): BASE_URL=http://localhost:8301 node scripts/install-screenshots.mjs [language ...]
//
// Each language's screens show one learner, as the app draws them for someone who has used it for a year and a half
// (scripts/install-screenshots/learner.mjs): 5000 words (words.tsv), seven collections, the travel one studied, eight
// lessons today, so the header holds a purple star and three gold ones, and an account online, so the sync is
// green. The learner's data is written into the page's databases through the development build's globals, as the
// browser specs seed theirs; every word's memory is the app's own (`domain.retention`) over a history of answers
// given in the learner's lessons, and lands in the bin the statistics count it in. The development build's D, build
// mark and trace export are hidden: a release draws none of them.
import { mkdirSync, readFileSync } from 'node:fs';
import { chromium } from '@playwright/test';
import { CITY_EXAMPLE, COLLECTIONS, DIALOG_WORD, LANGUAGES, TRAVEL } from './install-screenshots/learner.mjs';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8301';
const LOCALES = { ru: 'ru-RU', en: 'en-US', fr: 'fr-FR', tr: 'tr-TR' };

// The learner's 5000 words by the percent they are remembered, 0–10 % to 90–100 %: most of them well. The first
// bin's are the words not answered yet.
const BINS = [88, 76, 94, 120, 159, 234, 347, 571, 1060, 2251];
// Lessons on each of the last two weeks' days, today last: some days many, some few. Days before them are drawn.
const LAST_TWO_WEEKS = [3, 1, 5, 2, 4, 1, 6, 3, 2, 5, 1, 4, 7, 8];
const DAYS = 540;

const file = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

// The string of `key` in `language`'s dictionary.
const text = (language, key) => file(`src/shared/i18n/${language}.edn`).match(new RegExp(`${key}\\s+"([^"]*)"`))[1];

const WORDS = file('scripts/install-screenshots/words.tsv').trim().split('\n').map((line) => {
  const [value, pos, translation] = line.split('\t');
  return { value, pos, translation };
});

// Writes the learner into the page's databases. Runs in the page: the words' memory is computed by the app's code.
async function seed(page, language) {
  const travel = TRAVEL.map(({ sentence, sentenceTranslation, translation, usedForm, value }) => ({
    sentence, usedForm, value, sentenceTranslation: sentenceTranslation[language], translation: translation[language],
  }));
  const collections = COLLECTIONS.map(({ key, name, words }) => ({ key, name: name[language], words }));
  const city = { ...CITY_EXAMPLE, translation: CITY_EXAMPLE.translation[language] };
  return page.evaluate(async ({ bins, city, collections, days, dialogWord, language, lastTwoWeeks, travel, words }) => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    const retention = domain.retention;
    const weights = retention.default_weights;

    // A seeded generator, so that every run draws the same learner.
    let seedState = 826;
    const random = () => {
      seedState = (seedState + 0x6d2b79f5) | 0;
      let t = Math.imul(seedState ^ (seedState >>> 15), 1 | seedState);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
    const shuffled = (items) => {
      const out = [...items];
      for (let i = out.length - 1; i > 0; i--) {
        const j = Math.floor(random() * (i + 1));
        [out[i], out[j]] = [out[j], out[i]];
      }
      return out;
    };
    const uuid = () => Array.from({ length: 32 }, () => Math.floor(random() * 16).toString(16)).join('');
    const iso = (ms) => new Date(ms).toISOString();

    const now = Date.now();
    const HOUR = 3600000;
    const DAY = 24 * HOUR;
    const midnight = new Date(now);
    midnight.setHours(0, 0, 0, 0);

    // The lessons: on a day of the last two weeks as many as given, today's spread over the hours since midnight;
    // before them, one day in eight without a lesson and up to nine on the others, between seven and eleven at night.
    const lessonTimes = [];
    for (let age = days; age >= 0; age--) {
      const start = new Date(midnight);
      start.setDate(start.getDate() - age);
      if (age === 0) {
        const count = lastTwoWeeks[lastTwoWeeks.length - 1];
        for (let i = 1; i <= count; i++) lessonTimes.push(start.getTime() + ((now - start.getTime()) * i) / (count + 1));
        continue;
      }
      const count = age < lastTwoWeeks.length
        ? lastTwoWeeks[lastTwoWeeks.length - 1 - age]
        : (random() < 0.125 ? 0 : 1 + Math.floor(random() * 9));
      const times = Array.from({ length: count }, () => start.getTime() + 7 * HOUR + random() * 16 * HOUR);
      lessonTimes.push(...times.sort((a, b) => a - b));
    }
    // The last lesson at or before `ms`, by index.
    const lessonAtOrBefore = (ms) => {
      let low = 0;
      let high = lessonTimes.length - 1;
      if (lessonTimes[0] > ms) return -1;
      while (low < high) {
        const middle = Math.ceil((low + high) / 2);
        if (lessonTimes[middle] <= ms) low = middle;
        else high = middle - 1;
      }
      return low;
    };

    const recallOf = (value, createdAt, times, retained) => {
      const history = new retention.Reviews(
        Float64Array.from(times, (ms) => Math.floor(ms / 1000)),
        Uint8Array.from(retained, (kept) => (kept ? 1 : 0)),
        new Float64Array(times.length).fill(NaN),
        times.map((_, i) => `r${i}`),
        null, null, null);
      const word = cljs.core.hash_map(kw('value'), value, kw('created-at'), iso(createdAt));
      const memory = retention.memory(cljs.core.hash_map(kw('history'), history, kw('pace'), null, kw('weights'), weights, kw('word'), word));
      return retention.recall(cljs.core.hash_map(kw('memory'), memory, kw('now-ms'), now, kw('weights'), weights));
    };

    // A history of the answers `retained` ending in lesson `last`, `gaps` days apart, each answer in the lesson
    // nearest before its time. Nil when it would start before the first lesson.
    const historyEndingAt = (last, gaps, retained) => {
      const indices = [last];
      for (let i = gaps.length - 1; i >= 0; i--) {
        const index = Math.min(lessonAtOrBefore(lessonTimes[indices[0]] - gaps[i] * DAY), indices[0] - 1);
        if (index < 0) return null;
        indices.unshift(index);
      }
      return { times: indices.map((index) => lessonTimes[index]), retained };
    };

    // The answers, right (R) or wrong (W), that can leave a word in each bin: FSRS-34 forgets slowly, so a word
    // falls below half only after a wrong answer, below a third after several, and no answer at all leaves it at 0.
    const shapes = [
      [],
      ['WWW', 'WWWW', 'WWWWW', 'WWWWWW', 'RWWWW'],
      ['WW', 'WWW', 'RWWW'],
      ['W', 'WW', 'RWW'],
      ['W', 'RW', 'RWW'],
      ['RW', 'RRW', 'WR'],
      ['R', 'RRW', 'WR'],
      ['R', 'RR', 'RRR', 'WR'],
      ['RR', 'RRR', 'RRRRR', 'RWRR'],
      ['RR', 'RRR', 'RRRR', 'RRRRR', 'RRRRRR', 'RRWRR'],
    ];

    // A history after which the word is recalled within `bin`, tried on drawn shapes, the gaps between answers
    // growing.
    const historyIn = (value, bin) => {
      for (let attempt = 0; attempt < 100; attempt++) {
        const shape = shapes[bin][Math.floor(random() * shapes[bin].length)];
        const base = 1 + random() * 2;
        const gaps = Array.from({ length: shape.length - 1 }, (_, i) => base * 2.2 ** i * (0.7 + random() * 0.6));
        const retained = [...shape].map((answer) => answer === 'R');
        const target = (bin + 0.1 + random() * 0.8) / 10;
        const recallAt = (last) => {
          const history = historyEndingAt(last, gaps, retained);
          if (!history) return null;
          const createdAt = history.times[0] - HOUR - random() * 2 * DAY;
          return { ...history, createdAt, recall: recallOf(value, createdAt, history.times, retained) };
        };
        // The latest lesson whose history is recalled at most `target`: recall grows as the answers come later.
        let low = 0;
        let high = lessonTimes.length - 1;
        let found = null;
        while (low <= high) {
          const middle = Math.floor((low + high) / 2);
          const at = recallAt(middle);
          if (!at || at.recall < target) {
            if (at) found = at;
            low = middle + 1;
          } else {
            high = middle - 1;
          }
        }
        if (found && Math.min(9, Math.floor(found.recall * 100 / 10)) === bin) return found;
      }
      throw new Error(`No history puts ${value} in bin ${bin}`);
    };

    const vocabId = (value) => domain.vocabulary.vocab_id(value);
    const travelByValue = new Map(travel.map((word) => [word.value, word]));
    const collected = new Set(collections.flatMap(({ words }) => words));
    const translationOf = (word) => [{ lang: 'ru', value: travelByValue.get(word.value)?.translation ?? word.translation }];

    // Which bin each word lands in. The first holds the words never answered yet, added in the last days, none of
    // them in a collection.
    const order = shuffled(words.map((_, index) => index));
    const fresh = order.filter((index) => !collected.has(words[index].value)).slice(0, bins[0]);
    const freshSet = new Set(fresh);
    const answered = order.filter((index) => !freshSet.has(index));
    const binOf = new Map(fresh.map((index) => [index, 0]));
    let next = 0;
    bins.forEach((count, bin) => {
      if (bin === 0) return;
      for (let i = 0; i < count; i++) binOf.set(answered[next++], bin);
    });

    const docs = [];
    words.forEach((word, index) => {
      const bin = binOf.get(index);
      const translation = translationOf(word);
      if (freshSet.has(index)) {
        const createdAt = now - random() * 10 * DAY;
        docs.push({ _id: vocabId(word.value), type: 'vocab', value: word.value, pos: word.pos, translation, created_at: iso(createdAt), modified_at: iso(createdAt) });
        return;
      }
      const { createdAt, retained, times } = historyIn(word.value, bin);
      docs.push({ _id: vocabId(word.value), type: 'vocab', value: word.value, pos: word.pos, translation, created_at: iso(createdAt), modified_at: iso(createdAt) });
      times.forEach((ms, i) => docs.push({ _id: uuid(), type: 'review', word_id: vocabId(word.value), retained: retained[i], translation, created_at: iso(ms) }));
    });
    lessonTimes.forEach((ms) => docs.push({ _id: uuid(), type: 'lesson', passed_at: iso(ms) }));
    const ids = docs.map(({ _id }) => _id);
    if (new Set(ids).size !== ids.length) throw new Error('Two documents of the learner share an id');

    // The collections, made in the order given, three weeks apart from the first lesson on, each tile standing in
    // that order: every language's learner has the same colours.
    const collectionIds = new Map();
    collections.forEach(({ key, name, words: members }, position) => {
      const id = uuid();
      collectionIds.set(key, id);
      const createdAt = lessonTimes[0] + position * 21 * DAY;
      docs.push({ _id: id, type: 'collection', name, position, created_at: iso(createdAt), renamed_at: iso(createdAt), word_ids: members.map(vocabId) });
    });

    const user = db.use('user-db');
    for (let start = 0; start < docs.length; start += 2000) await db.bulk_docs(user, docs.slice(start, start + 2000));

    // The examples the app fetched for the travel words and for the dialog's word in the city, in this language.
    const example = async (value, collectionKey, { sentence, translation, usedForm }) => {
      const words = sentence.split(/\s+/).map((word) => word.replace(/^\p{P}+|\p{P}+$/gu, ''));
      await db.insert(user, adapters.learner.documents.example_doc(vocabId(value), value, collectionIds.get(collectionKey), toClj({
        language,
        structure: [{ dictionaryForm: value, translation: travelByValue.get(value).translation, usedForm, wordIndex: words.indexOf(usedForm) }],
        translation,
        value: sentence,
      })));
    };
    for (const word of travel) await example(word.value, 'travel', { ...word, translation: word.sentenceTranslation });
    await example(dialogWord, 'city', city);

    // The account: an identity in device-db, as an adopted key leaves it (test/browser/account.shared.js).
    await db.use('device-db').put({ _id: 'identity:local', type: 'identity', user_id: 1, token: 'stub-token' });
    localStorage.setItem('active-collection-id', collectionIds.get('travel'));

    const counts = Array(10).fill(0);
    binOf.forEach((bin) => counts[bin]++);
    return { bins: counts, docs: docs.length, lessons: lessonTimes.length, words: words.length };
  }, { bins: BINS, city, collections, days: DAYS, dialogWord: DIALOG_WORD, language, lastTwoWeeks: LAST_TWO_WEEKS, travel, words: WORDS });
}

// The account's copy on the server, a stub that holds nothing: every replication pass completes, so the sync is
// online (test/browser/account.shared.js).
async function stubAccountCopy(context) {
  const stub = { db_name: 'userdb-stub', update_seq: '0', instance_start_time: '0' };
  await context.route('**/db/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.includes('/_local/')) {
      return request.method() === 'GET'
        ? route.fulfill({ status: 404, json: { error: 'not_found', reason: 'missing' } })
        : route.fulfill({ status: 201, json: { ok: true, id: path.split('/_local/')[1], rev: '0-1' } });
    }
    if (path.endsWith('/_changes')) return route.fulfill({ json: { results: [], last_seq: '0' } });
    if (path.endsWith('/_revs_diff')) return route.fulfill({ json: {} });
    if (path.endsWith('/_bulk_docs')) return route.fulfill({ status: 201, json: [] });
    if (path.endsWith('/_bulk_get')) return route.fulfill({ json: { results: [] } });
    return route.fulfill({ json: stub });
  });
  // The examples the server would fetch for an account: the learner has theirs already.
  await context.route('**/api/examples**', (route) => route.fulfill({ json: [] }));
}

// Shoots the screen once it stands still: the sync online, today's four stars in, two frames painted and no
// animation running, the answer field's own included: its tiles fly inside its shadow root, which the document's
// animations leave out.
async function shoot(page, path) {
  await page.locator('.app-shell__sync--online').waitFor();
  await page.locator('.app-shell__star').nth(3).waitFor();
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await page.waitForFunction(() => [document, ...[...document.querySelectorAll('*')].flatMap((element) => element.shadowRoot ?? [])]
    .flatMap((root) => root.getAnimations())
    .every((animation) => animation.playState !== 'running' || animation.effect?.getTiming().iterations === Infinity));
  await page.screenshot({ path });
}

async function shootLanguage(browser, language) {
  const out = `resources/public/screenshots/${language}`;
  mkdirSync(out, { recursive: true });
  const context = await browser.newContext({
    baseURL: BASE_URL,
    colorScheme: 'light',
    deviceScaleFactor: 3,
    hasTouch: true,
    isMobile: true,
    locale: LOCALES[language],
    viewport: { width: 390, height: 844 },
  });
  await stubAccountCopy(context);
  // A release draws no D, build mark or trace export.
  const releaseLook = `.app-shell__dev-mark, .app-shell__build-mark, button[aria-label="${text(language, ':shell/export-trace')}"] { display: none !important; }`;
  await context.addInitScript((css) => {
    document.addEventListener('DOMContentLoaded', () => {
      const style = document.createElement('style');
      style.textContent = css;
      document.head.append(style);
    });
  }, releaseLook);
  // The lesson draws its words and its order at random: seeded, it is the same lesson on every run.
  await context.addInitScript(() => {
    let state = 2026;
    Math.random = () => {
      state = (state + 0x6d2b79f5) | 0;
      let t = Math.imul(state ^ (state >>> 15), 1 | state);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  });
  const page = await context.newPage();

  await page.goto('/home');
  await page.locator('.app-shell__bar').waitFor();
  const seeded = await seed(page, language);
  console.log(language, JSON.stringify(seeded));
  await page.reload();
  await page.locator('.app-shell__sync--online').waitFor();

  await page.goto('/collections');
  await shoot(page, `${out}/collections.png`);

  await page.goto('/stats');
  await page.locator('.statistics__day').first().waitFor();
  await shoot(page, `${out}/statistics.png`);

  await page.goto('/words');
  await page.locator(`[id="word-${await page.evaluate((value) => domain.vocabulary.vocab_id(value), DIALOG_WORD)}"] .word-item__display`).click();
  await page.locator('dialog[open]').waitFor();
  await shoot(page, `${out}/word.png`);

  // The lesson: each word answered right until a sentence comes, two of its tiles laid; at night, the next sentence
  // with three. The trial on screen is `#lesson-challenge`; the ones passed stay above it.
  const answers = new Map(TRAVEL.map(({ translation, value }) => [translation[language], value]));
  const sentences = new Map(TRAVEL.map(({ sentence, sentenceTranslation }) => [sentenceTranslation[language], sentence]));
  const bank = page.locator('#lesson-answer').getByRole('group', { name: text(language, ':lesson/tiles') });
  const prompt = page.locator('#lesson-challenge .lesson__prompt');
  let previous = null;
  const next = async () => {
    await page.locator('#lesson-main-button').click();
    await page.locator('#lesson-main-button', { hasText: text(language, ':lesson/next') }).click();
  };
  const nextSentence = async () => {
    for (;;) {
      await page.waitForFunction((before) => {
        const shown = document.querySelector('#lesson-challenge .lesson__prompt');
        return shown && shown.textContent.trim() !== before;
      }, previous);
      previous = (await prompt.innerText()).trim();
      if (sentences.has(previous)) {
        return sentences.get(previous).split(/\s+/).map((word) => word.replace(/^\p{P}+|\p{P}+$/gu, ''));
      }
      await page.locator('#lesson-answer').getByRole('textbox').fill(answers.get(previous));
      await next();
    }
  };
  const lay = async (tiles) => {
    for (const tile of tiles) await bank.getByRole('button', { name: tile, exact: true }).first().click();
  };
  await page.goto('/lesson');
  const first = await nextSentence();
  await lay(first.slice(0, 2));
  await shoot(page, `${out}/lesson.png`);

  await lay(first.slice(2));
  await next();
  const second = await nextSentence();
  await lay(second.slice(0, 3));
  await page.emulateMedia({ colorScheme: 'dark' });
  await shoot(page, `${out}/lesson-night.png`);

  await context.close();
}

const browser = await chromium.launch({ channel: 'chrome' });
const languages = process.argv.slice(2).length ? process.argv.slice(2) : LANGUAGES;
for (const language of languages) await shootLanguage(browser, language);
await browser.close();
