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

The overlap query is a lookup, not a concurrency guarantee. The future booking
service must validate inputs and perform the check and insert in one transaction.
To prevent two concurrent requests passing the same check, all booking writers
also need a common locking or equivalent database constraint strategy.

The repositories currently follow the scalar `branchId`/`userId` mappings in
the entity PR. Revisit those property paths when integrating Branch/User entities.

## Verified run

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
