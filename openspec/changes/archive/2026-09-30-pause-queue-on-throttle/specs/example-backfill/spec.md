## ADDED Requirements

### Requirement: A throttled answer pauses the whole example-fetch queue

When the examples endpoint answers that the device is throttled and names a Retry-After delay, the
task queue SHALL start no further example fetch until that delay has passed. Fetches already in
flight SHALL be allowed to finish. The refused fetch SHALL be queued again for the end of the delay;
the other queued fetches SHALL keep their schedule.

A trigger that arrives before the delay has passed — a newly queued fetch, a resume, a flush — SHALL
NOT start a fetch. When the delay has passed, the queue SHALL resume on its own and SHALL drain what
is due.

#### Scenario: The endpoint throttles a queue of many fetches

- **WHEN** ten fetches are due and the first answer is a throttle with a Retry-After delay
- **THEN** only the fetches already in flight are sent
- **AND** no further fetch is sent until the delay has passed

#### Scenario: A new fetch is queued during the pause

- **WHEN** a fetch is queued while the queue is paused by a throttle
- **THEN** it is not sent before the Retry-After delay has passed

#### Scenario: The delay passes

- **WHEN** the Retry-After delay has passed
- **THEN** the queue resumes without any further trigger and sends every due fetch, the refused one
  included
