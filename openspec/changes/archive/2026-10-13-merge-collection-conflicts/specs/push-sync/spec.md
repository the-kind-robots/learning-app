## ADDED Requirements

### Requirement: A pull that wrote collections resolves their conflicts
After a pass that pulled documents, the client SHALL resolve the conflicts of every collection among them as the collections requirement states, in the same pass as it resolves those of words. A pass that pulled nothing SHALL resolve nothing.

#### Scenario: A pull brings a sibling version
- **WHEN** a pass pulls a collection that this device changed too
- **THEN** the collection has one live version when the pass ends
