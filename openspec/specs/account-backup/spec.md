# account-backup Specification

## Purpose
TBD - created by archiving change local-first-invite-provisioning. Update Purpose after archive.

## Requirements

### Requirement: Provisioned account is seeded by first-sync push
The system SHALL seed a newly provisioned `userdb` by the device pushing its local `user-db` over normal replication; provisioning SHALL NOT upload a payload.

#### Scenario: Seed on provision
- **WHEN** an account is provisioned from a device holding local data
- **THEN** the device's next sync pass pushes that data into the fresh `userdb`

#### Scenario: Joining device sees the data
- **WHEN** a second device enters the account key after the seed push
- **THEN** replication delivers the first device's vocab, reviews, and collections

### Requirement: Key share is client-side
The system SHALL compose any share or email of the account key on the client, and the server SHALL NOT receive or store user email addresses for this purpose.

#### Scenario: Client-composed share
- **WHEN** a user shares their account key
- **THEN** the link is composed by the client via share, copy, or mailto
- **AND** the server does not receive the address
