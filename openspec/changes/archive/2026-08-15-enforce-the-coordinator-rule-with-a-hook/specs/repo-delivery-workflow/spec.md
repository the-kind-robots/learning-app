# repo-delivery-workflow Delta

## ADDED Requirements

### Requirement: A refusal stops the work and is reported

A refusal from a delivery guard or from workspace isolation SHALL stop the work that hit it
and SHALL produce a report to the owner naming the action attempted, what was refused, and
the verbatim text of the refusal. The owner decides what happens next.

A refusal SHALL NOT be circumvented. In particular:

- a commit SHALL NOT be assembled in a side or detached worktree and pushed straight to the
  branch ref because ordinary writes were blocked;
- a command SHALL NOT be reworded to slip past a static check instead of being run the
  permitted way;
- `DELIVERY_GUARD=off` SHALL NOT be set on the agent's own judgement, only on a direct
  request from the owner.

The guards inspect command text and the working directory, so they protect against accident,
not against intent. An agent that circumvents one does not defeat the protection — it defeats
the assurance that delivered work went down a verified path. Where the harness flags a move
only after the fact rather than preventing it, the absence of prevention SHALL NOT be read as
permission.

#### Scenario: An agent hits a refusal

- **WHEN** a guard or workspace isolation refuses a command or an edit
- **THEN** the agent stops that line of work and reports the action, the refusal and its
  verbatim text
- **AND** it does not retry the same intent by another route

#### Scenario: A route that is merely unguarded

- **WHEN** a path exists that the guard does not cover, such as a tool the matcher never sees
- **THEN** it is not used to accomplish what was just refused
