# Dictionary Glossary

Domain-specific terms used across the dictionary pipeline (`tools/dictionary/`).

| Term                   | Definition                                                                                                                                                         |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bare word**          | Word without article prefix. "der Hund" → "Hund". Generated as an additional surface form for noun autocomplete.                                                   |
| **CEFR**               | Common European Framework of Reference for Languages. Proficiency levels A1–C2. This project uses A1, A2, B1 from Goethe lists.                                    |
| **Dictionary entry**   | A document representing one lemma+POS combination with translations, rank, forms, and metadata. ID format: `lemma:{raw-lowercase-value}:{pos}`.                    |
| **form_of**            | Kaikki tag indicating a sense is a redirect to another lemma (inflected form, not a headword). Entries where all senses are form_of are excluded.                  |
| **Gender**             | Grammatical gender for nouns: "der" (masculine), "die" (feminine), "das" (neuter). Prepended to noun display values.                                               |
| **Goethe index**       | Stem-to-CEFR-level mapping from Goethe-Institut A1–B1 word lists (~3,900 stems).                                                                                   |
| **JSONL**              | JSON Lines format — one JSON object per line. Used for dictionary-entry and surface-form output files.                                                             |
| **Kaikki**             | Machine-readable Wiktionary extract (via wiktextract). Provides lemmas, POS, inflected forms, and translations.                                                    |
| **Lemma**              | The base/headword form of a word (e.g., "gehen" not "ging"). Distinguished from form-of redirects.                                                                 |
| **Manifest**           | `manifest.edn` — metadata file with entry counts, byte sizes, and SHA-256 checksums for output files.                                                             |
| **Merge**              | Combining duplicate Kaikki entries (same word+POS) by unioning translations and forms.                                                                             |
| **Normalized form**    | Lowercase with German special chars mapped to ASCII (ä→ae, ö→oe, ü→ue, ß→ss). Used as lookup keys.                                                                 |
| **POS**                | Part of speech — grammatical category (noun, verb, adj, adv, name, etc.). Stored lowercase.                                                                        |
| **Rank**               | Importance score. CEFR words get a base (A1=30k, A2=20k, B1=10k) plus bonuses for word frequency and senses/translations. Non-CEFR words max out at 5000.                             |
| **Sense**              | A distinct meaning of a Kaikki word entry. Each entry has a `:senses` vector; senses with `:form_of` are inflection redirects. Sense count feeds the rank formula. |
| **Slim entry**         | Stripped-down Kaikki entry with only needed fields, reducing memory during processing.                                                                             |
| **Stem**               | Root form used for CEFR matching. The Goethe index uses longest-prefix matching against stems.                                                                     |
| **Surface form**       | Any written variant of a word that a user might type — the lemma, article-prefixed form ("der Hund"), and all inflected forms. Used for autocomplete matching.     |
| **Surface-form index** | Map from normalized form → list of `{lemma-id, lemma, rank}`. Powers autocomplete prefix search.                                                                   |
