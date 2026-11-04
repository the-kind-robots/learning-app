## ADDED Requirements

### Requirement: The shell bar shows today's lessons as stars
The middle of the shell bar SHALL show, on every screen, a star for each lesson passed today, counted from the local midnight. Every five gold stars SHALL be shown as one purple star instead. From five purple stars on, the purple stars SHALL be shown as their number followed by one purple star. With no lesson passed today the slot SHALL show no star. The stars SHALL carry an accessible name with the number of lessons passed today. A star that appears SHALL light up once, and SHALL not move when the user asked for reduced motion.

#### Scenario: Three lessons today
- **WHEN** a learner has passed three lessons today
- **THEN** the shell bar shows three gold stars

#### Scenario: Seven lessons today
- **WHEN** a learner has passed seven lessons today
- **THEN** the shell bar shows one purple star and two gold stars

#### Scenario: Twenty-six lessons today
- **WHEN** a learner has passed twenty-six lessons today
- **THEN** the shell bar shows «5», one purple star and one gold star

#### Scenario: A lesson passed shows at once
- **WHEN** a learner answers the final trial of a lesson correctly
- **THEN** the shell bar has one star more, on the lesson screen and on home

#### Scenario: A new day
- **WHEN** the learner passed lessons yesterday and none today
- **THEN** the shell bar shows no star
