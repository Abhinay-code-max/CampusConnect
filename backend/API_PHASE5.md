# CampusConnect Phase 5 Event Management API

Base URL for the current local Tomcat deployment: `http://localhost:8080/CampusConnect`.
All request and response bodies are UTF-8 JSON. Error bodies use
`{"success":false,"message":"..."}`. Event dates are ISO-8601 date-times with a UTC marker or explicit offset, for example `2030-08-16T09:30:00+05:30` or `2030-08-16T04:00:00Z`.

## Public event browsing

### `GET /api/events`

Does not require a session. It returns only `PUBLISHED`, `CANCELLED`, and `COMPLETED` events; private `DRAFT` records are never returned. Cancelled and completed events remain visible as event history. Each item includes the current database registration count, confirmed-entry count, and a `registrationOpen` flag. `capacity` counts confirmed registration entries; an individual or a team each consumes one entry.

Query parameters:

| Parameter | Default / accepted values |
|---|---|
| `page` | 1 through 1,000,000; default 1 |
| `pageSize` | 1 through 100; default 20 |
| `q` | Optional event-name substring, maximum 100 characters |
| `participationMode` | `INDIVIDUAL` or `TEAM` |
| `startsAfter`, `startsBefore` | Optional ISO-8601 timestamp with offset; inclusive bounds on `startsAt` |
| `registrationOpen` | `true` or `false` |
| `sort` | `startsAt` (default), `eventName`, or `createdAt` |
| `direction` | `asc` (default) or `desc`; event ID breaks sort ties |

Example response (`200`):

```json
{"success":true,"events":[{"eventId":27,"eventCode":"ROBOTICS-2030","eventName":"Robotics Challenge","description":"Build and race a robot","participationMode":"TEAM","eventStatus":"PUBLISHED","registrationOpens":"2030-08-01T09:00Z","registrationCloses":"2030-08-10T17:00Z","startsAt":"2030-08-16T09:00+05:30","endsAt":"2030-08-16T17:00+05:30","capacity":30,"location":"Engineering Hall","createdAt":"2030-07-15T12:00Z","registrationCount":3,"confirmedCount":3,"registrationOpen":true}],"page":1,"pageSize":20,"total":1,"totalPages":1}
```

Invalid filters or out-of-range pagination return `400`.

### `GET /api/events/{eventId}`

Returns `200` and `{"success":true,"event":{...}}` for a visible event. A missing ID or private draft returns `404` with `{"success":false,"message":"Event not found"}`.

## Organizer/admin event management

All routes below require a logged-in `ORGANIZER` or `ADMIN`. The `JSESSIONID` cookie from Phase 4 login must be sent. State-changing requests require `X-CSRF-Token` with the token returned by `/api/auth/login` or `/api/auth/me`. A missing/invalid session returns `401`; other roles return `403`; a missing/invalid CSRF token returns `403`. Public registration still creates `STUDENT` accounts only.

### `GET /api/manage/events`

Returns a paginated result with the same query parameters as public listing. Organizers see only events they own, including drafts; admins see all events. Response fields and pagination shape match `GET /api/events`.

### `GET /api/manage/events/{eventId}`

Returns `200` with `{"success":true,"event":{...}}` for an event the authenticated organizer owns, including private drafts. Admins can retrieve any event. A different organizer receives `403`; a missing event receives `404`. Use this route to load private drafts for editing. The public `/api/events/{eventId}` route continues to return `404` for drafts.

### `POST /api/manage/events`

Creates a private `DRAFT`, owned by the authenticated organizer (or admin creating as the authenticated account). Owner identity is never accepted in request data. Required fields: `eventCode`, `eventName`, `participationMode`, `registrationOpens`, `registrationCloses`, `startsAt`, and `endsAt`. Optional fields: `description`, `capacity`, and `location` (null/omitted allowed).

- `eventCode`: unique; 1–40 ASCII letters/digits/dot/underscore/hyphen, starting with a letter or digit.
- `eventName`: 1–160 characters after trimming.
- `description`: at most 2,000 characters.
- `location`: at most 200 characters.
- `participationMode`: `INDIVIDUAL` or `TEAM`.
- Dates must satisfy `registrationOpens <= registrationCloses <= startsAt < endsAt`; registration close and event start must be in the future at create/edit time.
- `capacity`: null for unlimited, otherwise an integer from 1 through 99,999,999. It counts confirmed registration entries, not team members.

Example request:

```json
{"eventCode":"ROBOTICS-2030","eventName":"Robotics Challenge","description":"Build and race a robot","participationMode":"TEAM","registrationOpens":"2030-08-01T09:00:00+05:30","registrationCloses":"2030-08-10T17:00:00+05:30","startsAt":"2030-08-16T09:00:00+05:30","endsAt":"2030-08-16T17:00:00+05:30","capacity":30,"location":"Engineering Hall"}
```

Returns `201` with the created event and `eventStatus: DRAFT`. Duplicate event code returns `409`; invalid request returns `400`.

### `PUT /api/manage/events/{eventId}`

Replaces editable event fields; use the same full request shape as create, including the unchanged `eventCode`. Returns `200` with the updated event. An organizer editing another organizer’s event gets `403`; an unknown event gets `404`. Duplicate codes and edits conflicting with registrations/scores return `409`. Codes are immutable. `participationMode` cannot change once any registration row exists. Capacity cannot be reduced below confirmed registrations. Proposed registration dates must still contain all existing registration timestamps and the event start must remain after them. Any schedule field is frozen once scores exist. Cancelled/completed events are terminal and cannot be edited.

### Lifecycle actions

Each action is `POST`, has no body, and requires the session cookie plus CSRF header:

- `POST /api/manage/events/{id}/publish`: only `DRAFT → PUBLISHED` (`200`). Publishing makes the event public.
- `POST /api/manage/events/{id}/close-registration`: only a currently open `PUBLISHED` event. Sets `registrationCloses` to database time and retains `PUBLISHED`; visible history remains public, but `registrationOpen` becomes false (`200`). If registration has not opened, has already closed, or the event has started, returns `409`.
- `POST /api/manage/events/{id}/cancel`: `DRAFT → CANCELLED` or `PUBLISHED → CANCELLED` (`200`). Existing registrations/scores are retained; cancelled events remain publicly visible as history.

Unsupported/repeated transitions return `409`. `COMPLETED` is an existing terminal status; Phase 5 does not add a completion transition.

## Session and CSRF flow

1. `POST /api/auth/login` using the Phase 4 API. Keep the `JSESSIONID` cookie and the JSON `csrfToken`.
2. Send the cookie on management requests. Send `X-CSRF-Token: <csrfToken>` for `POST` and `PUT` management requests.
3. Cookies are HttpOnly and SameSite=Lax. Use same-origin frontend requests; no credentialed CORS was added.

## Response statuses

`200` successful read/update/action; `201` event created; `400` invalid fields, dates, filters, or JSON; `401` missing/invalid session; `403` wrong role, ownership, or CSRF; `404` missing event/action or public draft; `409` duplicate event code, conflicting edit, or unsupported lifecycle transition. Unexpected server failures return a generic `500` response; SQL diagnostics are logged without credentials or query values.
