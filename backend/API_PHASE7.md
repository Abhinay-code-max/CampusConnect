# CampusConnect Phase 7: judging, scores, and results

Base URL for local Tomcat: `http://localhost:8080/CampusConnect`. Requests and responses are UTF-8 JSON. Errors use `{"success":false,"message":"..."}`. Phase 7 uses the existing `JSESSIONID` session and role stored by the server at login. Preserve the cookie for the session. Every POST, PUT, and DELETE requires `X-CSRF-Token` using the token returned by login or `/api/auth/me`. Missing session is `401`; wrong role or assignment is `403`; missing or unpublished public resource is `404`; invalid input is `400`; lifecycle or business conflicts are `409`. Public result GETs do not require a cookie or CSRF token. Credentialed CORS is not enabled.

## Rules and calculations

- Only an event's owning ORGANIZER or an ADMIN may configure its judges, criteria, and scoring lifecycle. Organizers cannot manage another organizer's event.
- Only active accounts with role `JUDGE` may be assigned. Judges can access only events to which they are assigned and can create or update only their own score rows.
- Score configuration can be changed while an event is `DRAFT` or `PUBLISHED` and before the first score. After any score exists, assigned judges and criteria are frozen; there is no delete-criteria route. Scores and their history are preserved.
- An event can have up to 100 criteria. Criterion names are 1–120 characters, `maxPoints` is 0.01–999999.99, `weightPercent` is 0.01–100.00, and `displayOrder` is 1–9999. Decimal fields accept at most two decimal places. Criterion weights may be edited independently but must total **exactly 100.00** before finalization.
- The organizer or admin sets `scoringOpens` and `scoringCloses` as ISO-8601 date-times with a UTC marker or explicit offset. The window is inclusive. Scoring is accepted only while the event is `PUBLISHED`, the window is open according to Oracle `SYSTIMESTAMP`, the judge is still active and assigned, and the registration is a `CONFIRMED` registration for that same event. The score window is separate from registration dates and event start/end dates.
- A score batch has 1–100 distinct criteria. Each score must be between zero and that criterion's `maxPoints`. An invalid entry rejects the whole batch. A judge may later submit that criterion again to update their own score. The unique database constraint on `(registration_id, criterion_id, judge_user_id)` remains the final duplicate guard.
- Finalization is allowed only after the scoring window is closed, while the event is still `PUBLISHED`. It requires at least one criterion, assigned judge, and confirmed registration; weights totaling exactly 100; and a score from **every assigned judge for every criterion of every confirmed registration**. Judges must be active when assigned and when submitting scores; a later account deactivation does not erase a saved score. `WAITLISTED` and `CANCELLED` registrations are not scored. Missing scores are never interpreted as zero.
- Finalization changes the event to `COMPLETED` and sets `results_finalized_at`. Results remain private until the owner/admin separately publishes them, setting `results_published_at`. Only then does the public results endpoint return them. Participants can access the same event results after publication; unpublished results are not exposed to participants.
- The existing `V_CC_EVENT_RESULTS` formula is preserved: for each criterion, average the assigned judges' awarded points; divide the average by the criterion maximum; multiply by its percentage weight; sum the weighted criterion values; round to two decimal places. For example, a 9/10 average at 60% plus an 18/20 average at 40% yields `90.00`. The view uses `DENSE_RANK`, so equal scores share a rank and the next distinct score receives the next consecutive rank. Results are only exposed after completeness has been enforced by finalization.
- Migration `database/08_phase7_scoring.sql` adds nullable scoring-window and result-lifecycle timestamps. It backfills finalization/publication timestamps only for already `COMPLETED` events with complete scores and weights. It does not change scores or the existing result formula. The verified demo results remain `DEMO-TEAM-2026 = 85` and `DEMO-IND-2026 = 90`.

## Organizer/admin API

All routes require ORGANIZER or ADMIN, the session cookie, and CSRF on state-changing methods. The DAO checks event ownership on every operation.

### Judges

- `GET /api/manage/scoring/events/{eventId}/judges` — list assigned judge IDs, display names, role/active state, and assignment time (`200`).
- `POST /api/manage/scoring/events/{eventId}/judges` — assign an active judge. Body: `{"judgeUserId":41}`. Returns `201` and the assignment. A duplicate assignment or invalid/inactive role returns `409`.
- `DELETE /api/manage/scoring/events/{eventId}/judges/{judgeUserId}` — unassign (`200`). Assignment changes are rejected with `409` after any score exists; an absent assignment returns `404`.

### Criteria and scoring window

- `GET /api/manage/scoring/events/{eventId}/criteria` — ordered criteria (`200`).
- `POST /api/manage/scoring/events/{eventId}/criteria` — create (`201`). Example: `{"criterionName":"Presentation","maxPoints":10,"weightPercent":60,"displayOrder":1}`.
- `PUT /api/manage/scoring/events/{eventId}/criteria/{criterionId}` — edit the same fields (`200`). A duplicate name or post-score change returns `409`.
- `PUT /api/manage/scoring/events/{eventId}/scoring-window` — set the window (`200`). Example: `{"scoringOpens":"2026-10-10T09:00:00+05:30","scoringCloses":"2026-10-10T18:00:00+05:30"}`. `scoringCloses` must be in the future when configured. Both times are required and opens must precede closes. The window cannot be changed after the first score.
- `POST /api/manage/scoring/events/{eventId}/close-scoring` — close an open window early (`200`). It is safe to call after the configured close time; scoring remains closed.

### Finalization and results

- `POST /api/manage/scoring/events/{eventId}/finalize-results` — finalize (`200`). Example response: `{"success":true,"event":{"eventId":52,"eventStatus":"COMPLETED","resultsFinalizedAt":"2026-10-10T12:30:00Z"}}`. Incomplete scores, wrong weight total, an open scoring window, or a repeated finalization return `409`.
- `POST /api/manage/scoring/events/{eventId}/publish-results` — publish finalized results (`200`). Repeated publication or publication before finalization returns `409`.
- `GET /api/manage/scoring/events/{eventId}/results?page=1&pageSize=50` — owner/admin result preview after finalization, whether or not published (`200`). This route requires authentication and role authorization.
- `GET /api/results/events/{eventId}?page=1&pageSize=50` — public result page only after publication (`200`); unpublished or unknown events return `404`.

Results paging uses `page` 1–1,000,000 (default 1) and `pageSize` 1–100 (default 50). A success body contains `results`, `page`, `pageSize`, `total`, and `totalPages`. Each row includes event/registration IDs, registration type, registrant display name, weighted score, criteria scored, and dense rank. Email, password, and feedback are not included in public results.

## Judge API

Every route requires an authenticated `JUDGE`. The database also verifies that the account is active and currently assigned to the event.

- `GET /api/judging/events` — list active assigned events (`200`).
- `GET /api/judging/events/{eventId}` — view the event, criteria, scoring window, and eligible confirmed registrations (`200`). Participant details are limited to registrant display name/team name; no email is returned.
- `GET /api/judging/events/{eventId}/registrations/{registrationId}/scores` — list this judge's own scores for an eligible registration (`200`).
- `PUT /api/judging/events/{eventId}/registrations/{registrationId}/scores` — atomically create/update this judge's score entries (`200`). Example:

```json
{
  "scores": [
    {"criterionId": 81, "awardedPoints": 9.5, "feedback": "Clear explanation"},
    {"criterionId": 82, "awardedPoints": 17, "feedback": "Strong execution"}
  ]
}
```

Success returns `{"success":true,"registrationScores":{"eventId":52,"registrationId":73,"scores":[...]}}`. A score above its criterion maximum, repeated criterion ID in a batch, or malformed number returns `400`; cross-event criterion/registration, unconfirmed registration, or closed scoring returns `409`. An unassigned or inactive judge returns `403`. If any batch entry fails, no score from that batch is committed.

## Session setup

1. Log in with `POST /api/auth/login`; retain the `JSESSIONID` cookie and `csrfToken` from the JSON response.
2. Send the cookie on all protected requests. Include `X-CSRF-Token` for every POST, PUT, and DELETE. `GET` requests need no CSRF header.
3. `GET /api/auth/me` returns the current account and CSRF token if needed. Logout remains `POST /api/auth/logout` with the session cookie and CSRF token.
