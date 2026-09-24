# example-fetch-error-clarity Specification

## Purpose
Define how example fetching handles a malformed response or an unavailable generator, so neither becomes a broken example saved on the device.
## Requirements
### Requirement: Example fetch rejects malformed success payloads before local save
The system SHALL reject malformed example payloads before attempting to save them as local example documents.

#### Scenario: Backend success body misses required fields
- **WHEN** `/api/examples` returns a success response that lacks required example fields
- **THEN** the client rejects the payload before `save-example!`
- **AND** the example-fetch task is treated as failed and retryable

### Requirement: Example generation unavailability is surfaced explicitly
The system SHALL return an explicit non-success response when backend example generation is unavailable.

#### Scenario: Development backend has no API key
- **WHEN** example generation is unavailable because required backend configuration is missing
- **THEN** `/api/examples` returns a non-200 response with a clear error message
- **AND** the resulting client/task warning points to the real availability problem rather than `missing required fields`

