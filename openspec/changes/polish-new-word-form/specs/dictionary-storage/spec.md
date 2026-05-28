# dictionary-storage Specification

## ADDED Requirements

### Requirement: Build auto-applies enrichment when the side-file is present
The dictionary build SHALL chain `apply-enrichment!` after writing `dictionary.sqlite` when `enrichment-output.jsonl` exists in the output directory. When the side-file is absent (e.g. on a CI/build machine without the persistent enrichment record), the build SHALL continue to produce a deployable artifact without raising an error.

#### Scenario: Build auto-applies enrichment from the side-file
- **WHEN** `clojure -X:dictionary build` runs against an `output-dir` that contains `enrichment-output.jsonl`
- **THEN** after writing `dictionary.sqlite` the build calls `apply-enrichment!` against the same directory
- **AND** the resulting `dictionary.sqlite` contains the gap-fill translations
- **AND** the manifest reflects the post-enrichment file size and hash

#### Scenario: Build with no side-file is unchanged
- **WHEN** `clojure -X:dictionary build` runs against an `output-dir` without `enrichment-output.jsonl`
- **THEN** the build completes without attempting enrichment and without erroring
- **AND** the resulting `dictionary.sqlite` is deployable as-is per the existing contract

### Requirement: Surface-form materializer excludes cross-reference forms
The dictionary materializer SHALL exclude Kaikki form entries whose tags identify them as cross-references rather than true inflections. Specifically, forms tagged with any of `feminine`, `masculine`, or `abbreviation` SHALL NOT be indexed into the `surface_forms` table. True inflectional forms (`nominative`, `genitive`, `dative`, `accusative`, `singular`, `plural`, verb forms, etc.) SHALL continue to be indexed.

#### Scenario: Feminine cross-reference is not indexed under masculine lemma
- **WHEN** a noun lemma (e.g. "Vater") has a form entry tagged `feminine` (e.g. "Mutter")
- **THEN** that form is excluded from `surface_forms`
- **AND** searching `surface_forms` for `normalized_form = 'mutter'` does NOT return the "Vater" lemma

#### Scenario: Abbreviation form is not indexed
- **WHEN** a lemma has a form entry tagged `abbreviation` (e.g. "Angest." for "Angestellter")
- **THEN** that form is excluded from `surface_forms`

#### Scenario: Regular inflections still indexed
- **WHEN** a noun lemma has form entries with inflectional tags (e.g. "Hundes" tagged `genitive`, `singular`)
- **THEN** those forms are indexed in `surface_forms` normally
- **AND** searching for their prefix returns the lemma
