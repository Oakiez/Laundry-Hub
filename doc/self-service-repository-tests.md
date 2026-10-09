# Self-service repository tests

`SelfServiceRepositoryTest` runs real JPA queries against PostgreSQL 16.
It is opt-in so the ordinary unit test suite does not require Docker.

From the repository root in PowerShell:

```powershell
docker compose -f code/docker-compose.yml up -d db
$env:LAUNDRY_DB_TESTS = 'true'
mvn -f code/pom.xml test
Remove-Item Env:LAUNDRY_DB_TESTS
```

The default test connection is `jdbc:postgresql://localhost:5433/laundryhub`,
with local development user/password `laundry`. Override with `TEST_DB_URL`,
`TEST_DB_USER`, and `TEST_DB_PASSWORD` only for a dedicated test database.
Do not point these tests at a production database.

Each run creates a unique `self_service_test_<uuid>` schema, applies the
project's Flyway migrations there, and validates the entity mappings.
Each test transaction rolls back. At the end, only that generated schema
is dropped. Existing application tables in `public` are not used or changed.
The database user needs permission to create schemas. If the test JVM is
forcefully terminated, its temporary schema may remain for manual cleanup.

Coverage:

- Partial overlap, containment, identical intervals, separated intervals,
  and adjacent intervals on both sides.
- Only `RESERVED` and `IN_USE` sessions block a booking.
- A booking for another machine does not block this machine.
- An empty schedule permits a booking.
- Persist/reload of `UsageSession`, `Payable` values and creation timestamp.
- Machine/user session lookup, machine filters, pagination and sorting.
- Duplicate machine names within a branch, excluding the current machine
  when checking an update.
- Persisted Branch/User associations and deletion without cascading to shared entities.
- Machine lock lookup and detection of session history (including completed sessions).

The overlap query is a lookup, not a concurrency guarantee. The future booking
service must validate inputs and perform the check and insert in one transaction.
To prevent two concurrent requests passing the same check, all booking writers
also need a common locking or equivalent database constraint strategy.

The entities now reference Foundation's Branch/User through `ManyToOne LAZY`.
Queries use `branch.id` / `user.id`; no schema migration is needed because the
existing foreign-key columns are unchanged. MachineService maps responses inside
its transaction. The test fixture filters its own branch so V2 seed machines do
not change its expected counts.

MachineService acquires `findByIdForUpdate` before update/delete/status changes.
The future SessionService must acquire the same lock before overlap checks and
lifecycle changes. The lock lookup test alone is not a concurrent-booking test.

## Verified run after Foundation and MachineService integration

On 2026-10-09 at 21:38 (+07:00), after merging develop at `f95598f`:
166 tests passed, with zero failures, errors, or skipped tests.
This includes 19 PostgreSQL repository test invocations and 18 MachineService
unit test invocations. The complete suite includes the team's Foundation and
Payment tests. Both Flyway V1 and V2 ran in the isolated schema, followed by
Hibernate mapping validation. The machine lock lookup is covered, but competing
booking transactions still need a concurrency test when SessionService is added.

The local execution log is `code/target/machine-service-full-tests.log` (ignored
build output). The first attempt could not connect because Docker/DB was stopped;
the verified result above is the rerun after starting the DB container. The same
Mockito javaagent workaround described below was used.

## Earlier verified run (before Foundation integration)

On 2026-10-09, Java 17 / PostgreSQL 16: 83 tests passed (66 existing tests
plus 17 repository integration cases), with no failures, errors or skips.
The agent's restricted execution environment stalled during Mockito's dynamic
agent attachment. The successful run preloaded the project's resolved Mockito
5.17.0 agent for the test JVM using this additional Maven argument:

```powershell
"-DargLine=-javaagent:$env:USERPROFILE\.m2\repository\org\mockito\mockito-core\5.17.0\mockito-core-5.17.0.jar"
```

This was a command-line workaround only; no project dependency or JVM setting
was changed. Use the normal command above first in a regular terminal. Keep
`LAUNDRY_DB_TESTS=true` set when running the database cases.
