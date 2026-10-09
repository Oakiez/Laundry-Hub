# Self-Service REST API

All endpoints use the shared authentication and JSON error handling. Read access
requires login under the team's SecurityConfig; request matchers are unchanged.
Use HTTP Basic in Swagger's Authorize dialog.

## Machines

| Method | Endpoint | Access | Success |
|---|---|---|---|
| GET | `/api/v1/machines` | Authenticated | 200, PageResponse |
| GET | `/api/v1/machines/{id}` | Authenticated | 200 |
| POST | `/api/v1/machines` | ADMIN | 201 |
| PUT | `/api/v1/machines/{id}` | ADMIN | 200 |
| DELETE | `/api/v1/machines/{id}` | ADMIN | 204 |
| PATCH | `/api/v1/machines/{id}/status` | STAFF/ADMIN | 200 |

Search supports branchId/status/type and page/size/sort, e.g.
`?status=AVAILABLE&type=WASHER&page=0&size=10&sort=name,asc`.
Allowed machine sort fields: id, name, machineType, status, basePrice, pricePerMinute.
Unknown/nested sort fields are rejected with 400 before querying.

Create/update body:

```json
{"branchId":1,"name":"Washer 1","machineType":"WASHER","basePrice":20,"pricePerMinute":1.5}
```

Status body: `{"status":"OUT_OF_SERVICE"}` or `{"status":"AVAILABLE"}`.
Manual IN_USE/RESERVED changes are rejected; these are not maintenance actions.
Setting a running machine out of service returns 409. Duplicate names within
a branch return 409. Machines with session history cannot be deleted.

## Sessions

| Method | Endpoint | Access | Success |
|---|---|---|---|
| POST | `/api/v1/machines/{machineId}/sessions` | CUSTOMER/STAFF/ADMIN, owner check in service | 201 |
| GET | `/api/v1/machines/{machineId}/sessions` | STAFF/ADMIN | 200, PageResponse |
| GET | `/api/v1/users/{userId}/sessions` | Owner | 200, PageResponse |
| GET | `/api/v1/sessions/{id}` | Owner or STAFF/ADMIN | 200 |
| PATCH | `/api/v1/sessions/{id}/start` | Owner or STAFF/ADMIN | 200 |
| PATCH | `/api/v1/sessions/{id}/finish` | Owner or STAFF/ADMIN | 200 |
| PATCH | `/api/v1/sessions/{id}/cancel` | Owner or STAFF/ADMIN | 200 |

Booking body (choose a future startTime):

```json
{"userId":3,"startTime":"2030-01-01T10:00:00","durationMinutes":30}
```

Only an enabled CUSTOMER can own the booking. Customers book for themselves;
staff/admin can book on behalf of a customer. Duration is 10–180 minutes. Server
calculates amount/endTime; caller identity and staff privileges come from the
authenticated principal. Query/JSON values cannot grant staff access.

History defaults to startTime descending and supports page/size/sort. Allowed
session sort fields: id, startTime, endTime, status, amount, createdAt.

Overlap returns 409; forbidden ownership returns 403; missing resources return
404; validation and invalid lifecycle states return 400 under the shared
BusinessRuleException handler. The brief's lifecycle table suggests 409, whereas
its unit-test contract specifies BusinessRuleException: this implementation keeps
the shared 400 mapping and documents the difference for review.

Cancellation applies only to RESERVED. Start checks session/machine states;
it does not require prior payment or enforce a start-time window.
Payment uses the existing `/api/v1/payments` endpoint with USAGE_SESSION.

## Validation

2026-10-10, integrated develop d7e7fe7: **321 tests, zero failures/errors/skips**,
BUILD SUCCESS, with LAUNDRY_DB_TESTS=true. Machine/Session MVC tests contribute
18 cases. A PostgreSQL HTTP integration test covers booking, overlap 409,
non-owner 403, COIN payment, start, finish, history and machine release.
It also verifies four persisted notifications (booking/payment/start/finish).
Database tests use generated isolated schemas and do not mutate public data.
The local runner uses the cached Mockito Java agent as documented in
`doc/test-report/session-lifecycle-pond.md`. These are local results; remote CI
and Docker build for these new commits have not yet run.
