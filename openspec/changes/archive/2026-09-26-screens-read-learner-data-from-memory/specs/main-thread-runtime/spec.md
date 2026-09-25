## MODIFIED Requirements

### Requirement: Nexus dispatches all actions and effects
The system SHALL use Nexus as the mechanism for mutating app state; UI events emit action maps, effect handlers perform side effects and write results into state.

#### Scenario: UI event dispatches an action
- **WHEN** the user interacts with a UI element carrying a Nexus action
- **THEN** Nexus dispatches the action map using the runtime app context as its `system` value
- **AND** registered effect handlers can access app capabilities through effect context or the runtime app context
- **AND** the effect handler writes its result into the app-state store when state changes are needed

#### Scenario: Navigation route loads page data
- **WHEN** the browser route changes to `/home`, `/words`, `/lesson` or `/collections`
- **THEN** the `reitit.frontend` controller dispatches the page's entry through runtime dispatch
- **AND** the entry computes the page slice from the learner's data in app state, synchronously, without a storage read
- **AND** Replicant renders the page view in the same task
