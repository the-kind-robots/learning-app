// Writes scripts/install-screenshots/words.tsv: the 5000 words of the learner the install offer's screenshots show
// (#826), `value<TAB>pos<TAB>translation`, from the full dictionary, which the repository does not hold.
// Run from the repository root: node scripts/install-screenshots/words.mjs <path to dictionary.sqlite>
//
// The words are the dictionary's most frequent A1–B1 entries that a learner adds: one entry per spelling, a verb
// before an adjective before a noun (`leben`, not `das Leben`); no noun made of a function word (`das Ich`), no
// abbreviation in capitals, no inflected form standing as an entry (`die Habe`), none of STOP, and every word the
// collections name. The translation is the dictionary's first.
import { writeFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { COLLECTED, TRAVEL } from './learner.mjs';

const COUNT = 5000;
const ARTICLE = /^(der|die|das) /;
const PRIORITY = { verb: 0, adj: 1, noun: 2, adv: 3, prep: 4, conj: 5 };
const BLOCKING = new Set(['pron', 'conj', 'particle', 'det', 'article', 'intj']);
// Frequent entries the rules above let through that no learner has: homographs of forms and of names.
const STOP = new Set(['der Von', 'sind', 'einen', 'das Jetzt', 'das Aus', 'das Hab', 'der Einer', 'das Des', 'das Viel',
  'lass', 'das Hallo', 'das Mach', 'paar', 'freund', 'unter-', 'seid', 'das Morgen', 'das Gestern', 'Werd', 'schönen',
  'der Dan', 'plan', 'das Fort', 'die Miss', 'der Erste', 'der Scheiß', 'scheiße', 'gar', 'ein']);

const db = new DatabaseSync(process.argv[2], { readOnly: true });
const bare = (value) => value.replace(ARTICLE, '').toLowerCase();
const translationOf = db.prepare('select value from translations where lemma_id = ? order by rank limit 1');
const formsOf = db.prepare('select lemma_id from surface_forms where normalized_form = ?');

const lemmas = new Map(db.prepare('select id, value, pos from lemmas').all().map((lemma) => [lemma.id, lemma]));
const blocking = new Set([...lemmas.values()].filter(({ pos }) => BLOCKING.has(pos)).map(({ value }) => bare(value)));
const inflectionOfAnother = (id, value) =>
  formsOf.all(bare(value)).some(({ lemma_id: other }) => other !== id && bare(lemmas.get(other).value) !== bare(value));

const rows = db.prepare(`
  select id, value, pos from lemmas
  where cefr_level in ('a1', 'a2', 'b1') and pos in ('noun', 'verb', 'adj', 'adv', 'prep', 'conj')
    and exists (select 1 from translations t where t.lemma_id = lemmas.id)
  order by rank desc, id`).all();

// One entry per spelling, the first in frequency, of the part of speech that comes first.
const best = new Map();
rows.forEach((row, order) => {
  const held = best.get(bare(row.value));
  if (!held) best.set(bare(row.value), { order, row });
  else if (PRIORITY[row.pos] < PRIORITY[held.row.pos]) held.row = row;
});

const frequent = [...best.values()]
  .sort((a, b) => a.order - b.order)
  .map(({ row }) => row)
  .filter(({ id, pos, value }) => {
    const word = value.replace(ARTICLE, '');
    if (STOP.has(value) || word.includes(' ')) return false;
    if (pos === 'noun' && (word === word.toUpperCase() || blocking.has(bare(value)))) return false;
    return !inflectionOfAnother(id, value);
  });

// The collections' words, by their own spelling: the travel words carry their part of speech, the rest take the
// dictionary's entry of that spelling.
const travel = new Map(TRAVEL.map(({ value, pos }) => [value, pos]));
const collected = COLLECTED.map((value) => {
  const entry = rows.find((row) => row.value === value)
    ?? db.prepare('select id, value, pos from lemmas where value = ? order by rank desc').get(value);
  if (!entry) throw new Error(`Not in the dictionary: ${value}`);
  return { ...entry, pos: travel.get(value) ?? entry.pos };
});

const named = new Set(COLLECTED);
const words = [...collected, ...frequent.filter(({ value }) => !named.has(value))].slice(0, COUNT);
const lines = words.map(({ id, pos, value }) => [value, pos, translationOf.get(id).value].join('\t'));
writeFileSync(new URL('./words.tsv', import.meta.url), `${lines.join('\n')}\n`);
console.log(`${lines.length} words`);
