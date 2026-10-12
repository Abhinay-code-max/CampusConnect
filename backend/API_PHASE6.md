# CampusConnect Phase 6 API

Phase 6 adds student team management and event registrations. Base URL for the local deployment is `http://localhost:8080/CampusConnect`. Bodies and responses are UTF-8 JSON. Errors use `{"success":false,"message":"..."}`. Every state-changing request needs the logged-in `JSESSIONID` cookie and `X-CSRF-Token` header. Obtain the token from `POST /api/auth/login` or `GET /api/auth/me`. The cookie remains HttpOnly; clients must preserve it in a cookie jar. Use same-origin requests; credentialed CORS is not enabled. Missing session is `401`, wrong role or ownership is `403`, missing/inaccessible object is `404`, invalid input is `400`, and a state/business conflict is `409`.

## Business rules

- A team owner is the student who created it. The owner is automatically the first member, which is the owner’s consent to join. Other students join only after accepting a team invitation. Student accounts are addressed by `userId`; no student directory or email lookup is exposed.
- Team size is 2–5 active STUDENT accounts, including the owner. There is no ownership transfer. Owners cannot leave or be removed. Teams are never deleted. A roster (including invitations accepted into the roster) is frozen after the first registration row for that team, even if that row is later cancelled. This preserves the historical participant set.
- Only the owner can invite/remove members, rename the team, or register/cancel the team. Only the invited student can accept or decline an invitation. Invitation states are `PENDING`, `ACCEPTED`, `DECLINED`, or `CANCELLED`; only one pending invite per team/student is allowed.
- Individual accounts may register themselves only for `INDIVIDUAL` events. A team owner may register their own team only for `TEAM` events. Every team member must be an active student. A student cannot be part of two active registrations (confirmed or waitlisted) for the same event, whether individually or via a team.
- Registration requires a `PUBLISHED` event and database time within `registration_opens` through `registration_closes`, before `starts_at`. Capacity counts registration entries: one individual or one whole team uses one place. `CONFIRMED` consumes capacity; `WAITLISTED` does not. If full, a new valid registration is waitlisted. After a confirmed cancellation, the oldest eligible waitlisted entry is promoted.
- Students can cancel their own individual registration; a team owner can cancel the team’s registration. Cancellation is allowed only while the event is `PUBLISHED`, before it starts, and while the registration has no scores. Cancelled rows are terminal and retained. Re-registration is not allowed because the existing filtered unique indexes include cancelled rows; this prevents rewriting history or bypassing those constraints. A later attempt returns `409`.
- Organizers can list registrations only for events they own; admins can list any event’s registrations. Lists show display names only for individual entries and team name for team entries. Private registration details are visible only to an individual registrant or current member of the registered team.

## Teams

### `GET /api/teams` and `GET /api/teams/{teamId}`

List teams the student owns or belongs to, or retrieve one such team. Details include member `userId` and `displayName`, never email. Example list response: `{"success":true,"teams":[{"teamId":42,"teamName":"Blue Team","ownerUserId":7,"memberCount":2}]}`.

### `POST /api/teams`

Create a team; creator is owner and initial member. Body: `{"teamName":"Blue Team"}`. Name is trimmed, required, and at most 120 characters. Returns `201` with `{"success":true,"team":{...}}`. Duplicate name for the same owner returns `409`.

### `PUT /api/teams/{teamId}`

Owner only; body uses `teamName`. Returns `200`. Rename is blocked once any event registration row references the team.

### `POST /api/teams/{teamId}/invitations`

Owner invites a known student user ID. Body: `{"inviteeUserId":18}`. Returns `201` and pending invitation. The invitee must be an active STUDENT, must not already belong to this team, and the team must have room. No email address is returned.

### `GET /api/team-invitations`

List the authenticated student’s pending invitations, including team name and owner user ID.

### `POST /api/team-invitations/{invitationId}/accept` or `/decline`

Only the addressed student can respond. Accept adds them to the roster atomically. Returns `200` and the resulting invitation status. A stale invitation or full/frozen team returns `404`/`409`.

### `DELETE /api/teams/{teamId}/invitations/{invitationId}`

Owner revokes a pending invitation. `DELETE /api/teams/{teamId}/members/{userId}` removes a member; owner cannot be removed. `POST /api/teams/{teamId}/leave` lets a non-owner leave. Roster changes are rejected after registration. All return `200`; invalid ownership is `403`, missing object `404`, and prohibited change `409`.

## Registrations

### `POST /api/registrations/individual`

Register the logged-in student for an individual event. Body: `{"eventId":27}`. Returns `201` with the registration and `registrationStatus` of `CONFIRMED` or `WAITLISTED`. Repeat active registration, closed window, unpublished event, ineligible mode, or existing participation in another active entry returns `409`.

### `POST /api/registrations/teams/{teamId}`

Owner registers their eligible team. Body is also `{"eventId":27}`. Team size, active roles, event mode, and every member’s other active registrations are checked inside the transaction. One team counts as one capacity entry.

### `GET /api/registrations?page=1&pageSize=20`

List the student’s individual entries and team entries they currently belong to. `page` is 1–1,000,000; `pageSize` is 1–100. Results are newest first. `GET /api/registrations/{registrationId}` returns one entry if the student is its participant or a registered team member; otherwise `404`.

### `POST /api/registrations/{registrationId}/cancel`

Cancel an eligible individual registration or owned team registration. Uses database time and retains the row; if a confirmed place is freed, the oldest still-eligible waitlisted entry is promoted. Returns `200`. Started/closed/cancelled/completed events, scored rows, duplicate cancellation, and non-owner cancellation are rejected (inaccessible ownership is returned as `404`).

## Organizer/admin registration listing

`GET /api/manage/event-registrations/{eventId}?page=1&pageSize=50` requires ORGANIZER or ADMIN. Pagination limits match the student list. Organizers receive `403` for another owner’s event; unknown event is `404`. Only minimal participant names/team names are returned.

## Session examples

1. `POST /api/auth/login` with the Phase 4 credentials. Preserve `JSESSIONID` and read `csrfToken` from JSON.
2. Send the cookie on all team and registration requests. Add `X-CSRF-Token` with that token for POST, PUT, and DELETE (including cancellation, invitation responses, member changes, and registration).
3. `GET /api/auth/me` returns the account and current CSRF token. Logout through `POST /api/auth/logout` with the same cookie and token.

Typical success response: `{"success":true,"registration":{"registrationId":81,"eventId":27,"registrationType":"TEAM","teamId":42,"registrationStatus":"CONFIRMED"}}`. Timestamps are returned as ISO-8601 UTC strings. Internal database errors return a generic `500`; SQL text, credentials, password metadata, and session secrets are not returned.
