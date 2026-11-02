# insider-signal: specification

A small UEBA (user and entity behaviour analytics) service. It ingests Windows security events and flags risky user behaviour. It is a focused prototype exploring the problem space, not a production system.

There is no user interface: consumers use the REST API.

## Domain

Events handled, identified by Windows Security event ID:

| ID | Meaning | Used for |
|---|---|---|
| 4624 | Successful logon | All detections and the baseline |
| 4625 | Failed logon | Brute-force detection |
| 4728 | User added to a security-enabled global group | Stored only (see Later) |
| 4732 | User added to a security-enabled local group | Stored only (see Later) |

Rules that apply throughout:

- User principal names and host names are case-insensitive and normalised to lower case.
- Hours of day and baseline days are UTC.
- `eventId` is unique per event. Redelivering an event must not change any result.

## Features

### F-01 Event ingest

`POST /api/v1/events` accepts a batch of events, publishes them to Kafka, and a consumer batch-inserts them into ClickHouse.

Acceptance criteria:

- A valid batch of 1 to 1000 events returns `202` with the accepted count, once Kafka has acknowledged the whole batch.
- An invalid batch returns `400` as an RFC 7807 problem, and nothing is published.
- If Kafka cannot acknowledge the batch, the response is `503` and the client may retry the batch.
- Events for one user are processed in order (the Kafka key is the UPN).
- Redelivered events keep one copy in ClickHouse and do not inflate the baseline.
- A malformed Kafka record is logged and skipped; later events still process.
- If a store is down, the batch is retried until it recovers; no events are lost.

### F-02 Brute force followed by a successful logon

Acceptance criteria:

- 5 or more 4625s for an account within the 5 minutes before a 4624, with no successful logon between them, raise a `HIGH` alert.
- A failure exactly 5 minutes before the logon counts; one a moment older does not.
- Four failures, or failures on other accounts, raise nothing.
- The alert is stored in Postgres as JSONB, with the IDs of the failures and the logon as evidence.
- A second logon straight after the first raises no second alert.
- Events out of order within a batch are still detected.
- Reprocessing a logon does not duplicate its alert.

### F-03 Unusual logon for this user

A ClickHouse materialised view keeps each user's baseline: logons per day, hour of day and host. Rules read the 30 UTC days before the logon's day. The current day is excluded so an event cannot vouch for itself.

Acceptance criteria:

- A logon in an hour with fewer than `minLogonsInHour` baseline logons raises a `MEDIUM` `off-hours` alert.
- A logon from a host missing from the baseline raises a `MEDIUM` `new-host` alert.
- Neither rule judges a user with fewer than `minBaselineLogons` baseline logons.
- Events older than 24 hours build the baseline but raise no alerts, so replaying history does not flood analysts.

### F-04 User risk score

`GET /api/v1/users/{upn}/risk` returns the score and the reasons behind it. `GET /api/v1/alerts` lists recent alerts, optionally for one user.

Acceptance criteria:

- The score is the sum of the scores of the user's alerts from the last 7 days, capped at 100.
- Each contributing alert appears as a reason.
- A user with no recent alerts scores 0.

### F-05 Rule management with live cache invalidation

Rules are stored in Postgres as JSONB and cached in-process with Caffeine. `GET /api/v1/rules` lists them; `PUT /api/v1/rules/{ruleId}` replaces a rule's configuration.

| Rule | Settings (defaults) |
|---|---|
| `brute-force` | `failureThreshold` (5), `windowMinutes` (5), `score` (60) |
| `off-hours` | `minBaselineLogons` (20), `minLogonsInHour` (1), `score` (30) |
| `new-host` | `minBaselineLogons` (20), `score` (30) |

Acceptance criteria:

- After a `PUT`, the next event is scored under the new configuration, without a restart.
- A change made on one instance evicts the cached rule on every instance, through Postgres `LISTEN/NOTIFY`.
- If the notification connection drops, reconnecting evicts everything; a 10-minute TTL bounds staleness in any other case.
- An unknown rule returns `404`.
- An out-of-range value returns `400` with a field-level message. Partial configs, unknown fields and fractional values also return `400`.

## Later

Deliberately not built yet:

- **F-06** Redis Streams adapter behind `EventStream`, to compare with Kafka.
- **F-07** C# reference `BruteForceRule.cs`, plus a parity test running both implementations against the same golden files.
- **F-08** Azure Container Apps deployment.
- **F-09** Rule for privileged group changes (4728, 4732), especially straight after a suspicious logon.
