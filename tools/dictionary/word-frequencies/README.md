# Word Frequencies

Standalone import tool that turns a word-frequency source file into the
normalized `frequency.tsv` consumed by the main pipeline
(`tools/dictionary/dictionary/frequency.clj`).

SUBTLEX-DE is currently the only source. The tool is named generically so
additional sources (Leipzig Wortschatz, Tatoeba, ...) can be added without
renaming.

## Source

[OSF — SUBTLEX-DE project](https://osf.io/py9ba/files/osfstorage)

File: `SUBTLEX-DE cleaned version with Zipf values.xlsx`

Keep the XLSX outside the repo or under an ignored `data/` path.

## Run

```bash
clojure -T:word-frequencies generate \
  :input '"/path/to/SUBTLEX-DE cleaned version with Zipf values.xlsx"' \
  :output '"data/frequency.tsv"'
```

## Output schema

```tsv
word	count	source
für	4179.78	subtlex-de
Mädchen	468.32	subtlex-de
```

`count` is the per-million normalized count from the SUBTLEX corpus
(~25M subtitle tokens). Linear scale, so the consumer can sum counts
across surface-form variants of one lemma.

## Column choices in the XLSX

The file has 10 columns. We read only two.

| Column        | Used? | Notes |
|---------------|-------|-------|
| Word          | ✓     | German wordform |
| WFfreqcount   | —     | raw count; less portable than per-million |
| CUMfreqcount  | —     | cumulative |
| **SUBTLEX**   | ✓     | per-million normalized count |
| Google00      | —     | raw count from Google n-grams (different corpus) |
| Google00pm    | —     | Google per-million; web/book mix, less conversational than subtitles |
| ZipfSUBTLEX   | —     | log10 scale; can't sum across variants without logsumexp |
| ZipfGoogle    | —     | same caveat plus Google corpus |

## Why a hand-rolled XLSX parser

The file is one sheet with ~190k rows. XLSX is a ZIP of XML, so the
script reads it with the JDK's `ZipFile` + StAX. Apache POI would add
~10MB for a one-shot import; not worth the dep.

Shared strings ARE required: column A (`Word`) is encoded as `t="s"`
with sharedStrings indices. Numeric columns are inline.
