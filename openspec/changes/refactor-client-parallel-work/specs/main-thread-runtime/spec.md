## MODIFIED Requirements

### Requirement: Nexus dispatches all actions and effects
The system SHALL use Nexus as the mechanism for mutating app state; UI events emit action maps, effect handlers perform side effects and write results into state.

#### Scenario: UI event dispatches an action
- **WHEN** the user interacts with a UI element carrying a Nexus action
- **THEN** Nexus dispatches the action map using the runtime system as its `system` value
- **AND** registered effect handlers can access app deps through effect context or the runtime system
- **AND** the effect handler writes its result into the app-state store when state changes are needed

#### Scenario: Navigation route loads page data
- **WHEN** the browser route changes to `/home`, `/words`, or `/lesson`
- **THEN** the `reitit.frontend` controller dispatches the page loader effect through runtime dispatch
- **AND** the loader reads needed capabilities from deps instead of constructing global DB/worker handles
- **AND** the loader writes the page slice into state
- **AND** Replicant renders the page view
