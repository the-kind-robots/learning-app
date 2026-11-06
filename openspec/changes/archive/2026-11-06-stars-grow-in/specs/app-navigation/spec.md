## MODIFIED Requirements

### Requirement: The shell bar shows today's lessons as stars
The middle of the shell bar SHALL show, on every screen, a star for each lesson passed today, counted from the local midnight. Every five gold stars SHALL be shown as one purple star instead. From five purple stars on, the purple stars SHALL be shown as their number followed by one purple star. With no lesson passed today the slot SHALL show no star. The stars SHALL carry an accessible name with the number of lessons passed today. The bar SHALL draw the count it holds, and the stars SHALL move as they come and go. The stars shown when the app opens SHALL light up once and SHALL NOT grow. A star that joins the bar after — for a lesson passed here or on another device — SHALL grow in at the right, past its size and back, the stars before it moving left to make room, and SHALL finish its motion on whatever screen is shown. When the new star is the fifth gold one, it SHALL grow in gold and turn purple while the four gold stars before it gather into it. When that purple star brings the number of purple stars to five or more, the purple stars shown before SHALL gather with the gold ones, and the number SHALL appear before the purple star. Stars that do not fit the slot while they move SHALL draw over its edges and over the marks beside it, not be cut. With reduced motion asked for, no star SHALL move, and the bar SHALL show the stars of the new count at once.

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

#### Scenario: A new star grows in
- **WHEN** a learner with two lessons today passes a third
- **THEN** the third star grows larger than its size, then settles to it
- **AND** the two stars before it end further left than they stood

#### Scenario: Leaving the lesson at once
- **WHEN** a learner with two lessons today passes a third and presses `Escape` at once
- **THEN** on home the third star grows larger than its size, then settles to it

#### Scenario: A lesson passed on another device
- **WHEN** a lesson passed on another device reaches a learner on home with two lessons today
- **THEN** the third star grows larger than its size

#### Scenario: Five gold stars become a purple one
- **WHEN** a learner with four lessons today passes a fifth
- **THEN** a fifth gold star grows in right of the four
- **AND** then the four gather into it, it turns purple, and the bar shows one purple star

#### Scenario: The fifth purple star
- **WHEN** a learner with twenty-four lessons today passes a twenty-fifth
- **THEN** a fifth gold star grows in right of four purple and four gold stars
- **AND** then the eight gather into it, it turns purple, and the bar shows «5» and one purple star

#### Scenario: Opening the app on five lessons
- **WHEN** a learner with five lessons today opens the app
- **THEN** the shell bar shows one purple star, at its size from the first moment, and no gold star gathering

#### Scenario: A fifth lesson with reduced motion
- **WHEN** a learner who asked for reduced motion passes a fifth lesson today
- **THEN** the shell bar shows one purple star at once and no gold star
