## ADDED Requirements

### Requirement: A picked part of speech decides the dictionary entry of the example
The example request of a word whose document carries `pos` SHALL carry it as the `pos` parameter, and a word without it SHALL send none. When the request has `pos`, the lookup SHALL choose among the dictionary entries of that part of speech only, the entry whose gloss matches first, and the generation request SHALL carry that entry's lemma and part of speech. When the dictionary has no entry of that part of speech for the word, the lookup SHALL choose as it does without `pos`. A request without `pos` SHALL be answered as before. When the lookup used the picked part of speech, a generated example SHALL be rejected as invalid and regenerated when its `structure` names the word only as a lemma of another part of speech (`der Rauch` for the adjective `rauch`); a bare `dictionaryForm` that is also a lemma of the picked part of speech SHALL pass. An adjective and a verb count as one for this check, so a participle annotated as its verb (`explodierte` as `explodieren`) is right for a picked adjective, and the reverse. A word whose every example is rejected SHALL stay without an example rather than get one about another word. Without a picked part of speech there SHALL be no such check. Examples of the same word under different parts of speech SHALL NOT share a cached example.

#### Scenario: The adjective, not the noun
- **WHEN** an example is requested for "rauch" with `pos` of the adjective, and the dictionary has the adjective "rauch" and the noun "der Rauch"
- **THEN** the generation request carries the adjective's lemma and part of speech

#### Scenario: A picked adjective gets no example about the noun
- **WHEN** an example is generated for "rauch" with `pos` of the adjective and its `structure` names the word as "der Rauch"
- **THEN** the example is rejected as invalid and generation is retried, and a word whose examples are all rejected stays without one
- **AND** an example whose `structure` names it as "rauch" is accepted

#### Scenario: No part of speech picked
- **WHEN** an example is requested for "rauch" without `pos`
- **THEN** the entry is chosen by gloss, otherwise the first entry, as before

#### Scenario: Separate cache rows
- **WHEN** examples are requested for "rauch" as an adjective and as a noun
- **THEN** each is generated and stored under its own key
