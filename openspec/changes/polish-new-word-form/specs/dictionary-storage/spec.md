# dictionary-storage Specification

## ADDED Requirements

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
