## MODIFIED Requirements

### Requirement: Example documents include collection-id when generated under a named collection
The system SHALL store a `collection-id` field on example documents generated while a named (non-All-Words) collection is active. Documents generated under All Words SHALL omit the field.

#### Scenario: New example document under named collection includes collection-id
- **WHEN** an example is fetched for word W while collection T is active
- **THEN** the stored document includes `type: "example"`, `word-id`, `collection-id`, `word`, `value`, `translation` and `structure`

Example:
```json
{
  "type": "example",
  "word-id": "<vocab-id>",
  "collection-id": "collection-abc123",
  "word": "der Hund",
  "value": "Der Hund rennt über die Straße.",
  "translation": "The dog runs across the street.",
  "structure": []
}
```

#### Scenario: Example under All Words has no collection-id field
- **WHEN** an example is fetched for word W while All Words is active
- **THEN** the stored document is written without a `collection-id` field

## ADDED Requirements

### Requirement: An example's identity is its pair and its content

The system SHALL store an example under an id made of the pair it answers and of its content: `example:`, the entry's id, a colon, the collection's id, a colon, and a short hash of the example's sentence, translation and structure. An example generated under All Words SHALL carry an empty collection part. The hash SHALL NOT depend on the order in which the structure's fields were written.

A pair MAY have several examples, and every distinct one SHALL be kept. The same example stored twice — on one device or on two — SHALL be one document: both writes give it the same id, and since the document holds nothing that depends on the device or the time (`specs/data-model/spec.md`), the same revision, so replication leaves no conflict. A fetched example that is stored already SHALL leave the stored document as it is.

#### Scenario: An example under a named collection

- **WHEN** an example is fetched for entry `vocab:der hund` while collection `coll-tiere` is active
- **THEN** it is stored with an id of the form `example:vocab:der hund:coll-tiere:<hash>`

#### Scenario: An example under All Words

- **WHEN** an example is fetched for entry `vocab:der hund` while All Words is active
- **THEN** it is stored with an id of the form `example:vocab:der hund::<hash>`

#### Scenario: Two devices store the same example

- **WHEN** two devices of one account each store the same example for the same entry and collection
- **AND** they replicate
- **THEN** each device holds one document for it, with no conflict

#### Scenario: Two devices store different examples of one pair

- **WHEN** two devices of one account each store a different example for the same entry and collection
- **AND** they replicate
- **THEN** each device holds both, with no conflict

#### Scenario: The same example arrives again

- **WHEN** a fetched example arrives that is stored already
- **THEN** the stored document is kept unchanged
