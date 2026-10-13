## MODIFIED Requirements

### Requirement: A generated example for a phrase carries the whole construction
When the target of generation is a phrase, the generated German sentence SHALL contain the whole construction. It MAY appear inflected and rearranged by German word order, and its words need not be adjacent in the sentence. A phrase that is already a complete sentence SHALL be placed in a sentence that extends or embeds it rather than returned verbatim. An example whose sentence does not carry the whole construction SHALL be rejected as invalid and regenerated.

A placeholder of the phrase — `etwas`, `jemand`, `jemanden`, `jemandem`, `jemandes`, `sich`, the possessives `seine`, `seinen`, `seinem`, `seiner`, `seines`, the abbreviations of these (`etw.`, `jdn`, `jdm`, `jmdm` …) and `einer Sache` — is a slot, not a word: the sentence SHALL NOT be required to say it, and MAY fill it with any noun phrase or pronoun that fits, `sich` with any reflexive pronoun. The articles a phrase lists SHALL NOT be required either: the sentence puts the determiner its noun needs. Every other word of the phrase SHALL be required.

`structure` SHALL annotate the sentence word by word under the same rules whatever the target is, and SHALL NOT record which words belonged to the construction. Presence of a multi-word target is therefore checked against the sentence: every required word SHALL be present, matching either a word of the sentence or the `dictionaryForm` of some `structure` item (or a word of it), which is what carries inflection. A single-lemma target SHALL be present when `structure` names it by a `dictionaryForm`, or when the sentence says it in the form it is written in.

An imperfect `structure` SHALL NOT cost the example. An item SHALL be placed on the first matching word of the sentence after the previous item's, else on the first matching word before it; an item of two words SHALL count as one item per word; an item that matches no word of the sentence, or whose translation is not Russian, SHALL be left out of the stored `structure`. The stored `structure` SHALL be ordered by the position of the words in the sentence. An example none of whose items can be placed SHALL be rejected as invalid and regenerated.

#### Scenario: Inflected phrase inside a sentence
- **WHEN** an example is generated for the phrase "den Kopf verlieren"
- **THEN** a sentence such as "Er verliert den Kopf." is accepted
- **AND** "den" and "Kopf" are found in the sentence, and "verlieren" through the `structure` item whose `dictionaryForm` names it

#### Scenario: Phrase split by German word order
- **WHEN** an example is generated for the phrase "auf jeden Fall"
- **THEN** a sentence such as "Ich komme auf jeden Fall mit." is accepted although the construction sits inside the clause
- **AND** `structure` annotates the sentence's words under the ordinary rules, so "Fall" carries its own lemma and "auf" and "jeden" are left out as a preposition and a determiner

#### Scenario: A word the sentence says twice
- **WHEN** an example is generated for the phrase "von Zeit zu Zeit"
- **THEN** the example is accepted although "Zeit" occurs twice
- **AND** each occurrence is its own `structure` item, annotated as the noun it is, with its own `wordIndex`

#### Scenario: Phrase that is already a sentence
- **WHEN** an example is generated for a phrase that is itself a complete sentence
- **THEN** the generated sentence extends or embeds it into a turn rather than repeating it unchanged

#### Scenario: Construction missing from the output
- **WHEN** the generated sentence carries only some of the construction's words
- **THEN** the example is rejected as invalid and generation is retried, and the retry is told the same rule in the same words the prompt states it in

#### Scenario: A placeholder filled with an object
- **WHEN** an example is generated for the phrase "Anspruch auf etwas haben"
- **THEN** "Jeder Arbeitnehmer hat einen Anspruch auf Urlaub." is accepted although the sentence does not say "etwas"

#### Scenario: A reflexive placeholder
- **WHEN** an example is generated for the phrase "sich bei jemandem vorstellen"
- **THEN** a sentence with "mich", "dich", "uns" or any other reflexive pronoun in place of "sich" is accepted

#### Scenario: Another article than the phrase lists
- **WHEN** an example is generated for the phrase "ein Versprechen brechen"
- **THEN** "Ich werde mein Versprechen nicht brechen." is accepted

#### Scenario: A word that is no placeholder is missing
- **WHEN** an example is generated for the phrase "Bezug auf etwas nehmen" and the sentence says neither "Bezug" nor "nehmen"
- **THEN** the example is rejected as invalid and generation is retried

#### Scenario: A single word in the form it is written in
- **WHEN** an example is generated for the word "verzweifelt" and the sentence is "Er verzweifelt an der Aufgabe." with its verb annotated under the lemma "verzweifeln"
- **THEN** the example is accepted

#### Scenario: A structure item that cannot be placed
- **WHEN** a generated example annotates a word out of order, spells one as its lemma, or glosses one in English
- **THEN** the example is accepted and the stored `structure` holds the items that could be placed, in sentence order
