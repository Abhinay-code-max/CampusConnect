# CampusConnect Phase 8: administrator controls

Base URL: `http://localhost:8080/CampusConnect`. All request and response bodies are UTF-8 JSON. This API adds no frontend files.

## Authentication and authorization

Log in with `POST /api/auth/login`, preserve the `JSESSIONID` cookie, and retain the returned `csrfToken` (or get it from `GET /api/auth/me`). Every `/api/admin/*` route requires an active server-side `ADMIN` account. The API reloads the account's role and active flag from Oracle before every protected API request and replaces the session principal. A changed role therefore applies on that next request. A deactivated or missing account has its session invalidated and receives `401`. `POST`, `PUT`, `PATCH`, and `DELETE` requests need `X-CSRF-Token`; no credentialed CORS is enabled.

Errors use `{"success":false,"message":"..."}`. Statuses are `200` success, `400` invalid input, `401` unauthenticated/inactive, `403` wrong role, `404` missing user/route, `409` relationship or account-state conflict, and `500` a safe server error. User responses never include password hashes, salts, iteration counts, CSRF values, session identifiers, or other credential data.

## User management

Pagination is `page=1..1000000` and `pageSize=1..100` (defaults 1 and 25). Paginated responses include `page`, `pageSize`, `total`, and `totalPages`.

- `GET /api/admin/users?q=anita&role=STUDENT&active=true&sort=createdAt&direction=desc&page=1&pageSize=25`
- `GET /api/admin/users/{userId}`
- `PUT /api/admin/users/{userId}/role` with `{"role":"ORGANIZER"}`
- `PUT /api/admin/users/{userId}/status` with `{"active":false}`

`role` accepts only `STUDENT`, `ORGANIZER`, `JUDGE`, or `ADMIN`. `active` is a JSON boolean. User search is a case-insensitive substring match over display name and email. User-list sort accepts `displayName`, `email`, `role`, `active`, or `createdAt`; `direction` is `asc` or `desc`. Example success:

```json
{"success":true,"users":[{"userId":42,"displayName":"Anita Rao","email":"anita@example.edu","role":"STUDENT","active":true,"createdAt":"2026-10-10 08:30:00.000000 +00:00"}],"page":1,"pageSize":25,"total":1,"totalPages":1}
```

User details include the safe account fields above and counts for `teamMemberships`, `activeRegistrations`, `ownedEvents`, and `judgeAssignments`.

### Account management rules

- The `CC_ADMIN_CONTROL` singleton row is locked before every role or active-state mutation. Concurrent changes serialize on that row, and the active-admin count is checked while holding the lock. The last active `ADMIN` cannot be demoted or deactivated. No existing administrator is altered by this feature.
- Any role change is rejected while the account has a team membership or a `CONFIRMED`/`WAITLISTED` individual registration. A role change away from `ORGANIZER` is rejected while the account owns an event, except changing to `ADMIN`, which retains event-management authority. A role change away from `JUDGE` is rejected while any judge assignment exists. These relationships are retained; administrators should resolve the relationship through the existing event/team/registration workflows before retrying.
- Deactivation never deletes teams, memberships, registrations, events, scores, or results. The account cannot log in and existing sessions are rejected on their next protected request. Its event ownership remains unchanged; an administrator can still manage those events. Existing registrations/results remain historical and visible under existing policies, but a disabled student cannot perform student actions. A judge assigned to a `DRAFT` or `PUBLISHED` event cannot be deactivated until the assignment is removed. Completed/cancelled-event assignments and scores remain historical.
- Reactivation restores the account's prior role and relationships; it does not recreate credentials or data.
- Public registration remains `STUDENT`-only. No account is hard-deleted.

## Platform views

- `GET /api/admin/events?q=robot&mode=TEAM&startsAfter=2026-10-01T00:00:00Z&startsBefore=2026-12-31T23:59:59Z&registrationOpen=true&sort=startsAt&direction=asc&page=1&pageSize=25` lists all events, including drafts, using the existing event query/service. `mode` is `INDIVIDUAL` or `TEAM`. Date filters require ISO-8601 date-times with `Z` or an explicit UTC offset. Sort is `startsAt`, `eventName`, or `createdAt`.
- `GET /api/admin/registrations?eventId=42&status=CONFIRMED&page=1&pageSize=25` lists registrations across the platform. `eventId` is optional; `status` is optional and accepts `CONFIRMED`, `WAITLISTED`, or `CANCELLED`. Team and individual registrations are represented with the same fields; the unused participant/team ID is `null`.
- `GET /api/admin/dashboard` returns summary totals and grouped counts for users by role, events by lifecycle status, and registrations by status. It also reports active users, total scores, and published result events.

Example dashboard response:

```json
{"success":true,"dashboard":{"users":72,"activeUsers":71,"events":14,"registrations":22,"scores":60,"publishedResults":4,"usersByRole":[{"key":"ADMIN","count":9}],"eventsByStatus":[{"key":"PUBLISHED","count":3}],"registrationsByStatus":[{"key":"CONFIRMED","count":18}]}}
```

Counts above are illustrative, not a snapshot of the current database.

## Audit history

Each successful role/status change inserts its audit row in the same transaction as the account change. A failed validation or insert rolls back both. `GET /api/admin/audit?actorUserId=8&targetUserId=42&action=ROLE_CHANGE&page=1&pageSize=50` is read-only. Filters are optional; `action` accepts `ROLE_CHANGE`, `ACTIVATE`, or `DEACTIVATE`. Entries expose audit ID, actor and target IDs/display names, action, previous/new role or status, and timestamp. Historical rows are retained; the application has no audit deletion endpoint.

## Migration

`database/09_admin_controls.sql` is an additive migration for `CC_ADMIN_CONTROL` and `CC_ADMIN_AUDIT`, with primary/foreign/check constraints and lookup indexes. It was applied via JDBC only after checking `SESSION_USER=CAMPUSCONNECT`, `CURRENT_SCHEMA=CAMPUSCONNECT`, `CON_NAME=FREEPDB1`, and `CC_USERS` in the configured database connection. The migration is run-once DDL; if execution stops partway, inspect existing objects and execute only missing statements. Do not rerun blindly.
