# CampusConnect Phase 4 API

## Base URL

The WAR final name is `CampusConnect.war`; Tomcat deploys that application under `/CampusConnect` unless its context is explicitly overridden. The local Tomcat connector port must match your installation. With the standard local Tomcat connector, use `http://localhost:8080/CampusConnect`; use `https://` when testing through your HTTPS connector.

## Registration and login rules

- Email is trimmed and lowercased using `Locale.ROOT`. The same policy is enforced in Oracle by a unique index on `LOWER(TRIM(email))`.
- Registration accepts only `displayName`, `email`, and `password`. Any extra field, including `role`, is rejected. Publicly-created accounts always receive `STUDENT`.
- Display name: 1–120 Unicode characters after trimming; control characters are rejected.
- Email: ASCII address, maximum 254 characters.
- Password: 12–128 Unicode characters. Spaces are preserved. A passphrase is allowed; no character-class rules are imposed.
- Passwords are stored as per-user 16-byte random salts and 32-byte PBKDF2WithHmacSHA256 derived values at 600,000 iterations. Existing accounts with NULL credential columns cannot log in.
- The servlet JSON parser rejects unknown fields. JSON responses never include password, salt, or hash data.

## Endpoints

Every request and response uses UTF-8 JSON except logout still returns a JSON success body. Error shape: `{"success":false,"message":"..."}`.

### `POST /api/auth/register`

Body:

```json
{"displayName":"Example Student","email":"student@example.edu","password":"a long passphrase 2026"}
```

Success: `201 Created`, `{"success":true,"user":{"userId":123,"displayName":"Example Student","email":"student@example.edu","role":"STUDENT"}}`.

Duplicate normalized email: `409 Conflict`, `{"success":false,"message":"An account with that email already exists"}`.

Malformed JSON, missing fields, invalid email/name/password, or a requested `role`: `400 Bad Request` with a safe validation message.

Registration does not log in the user. Follow it with login.

### `POST /api/auth/login`

Body:

```json
{"email":"student@example.edu","password":"a long passphrase 2026"}
```

Success: `200 OK`, a safe `user` object and `csrfToken`; Tomcat sets an HttpOnly `JSESSIONID` cookie. Postman stores and resends this cookie automatically when cookie handling is enabled.

Wrong password, unknown account, inactive account, and an account without credentials all return the same `401 Unauthorized` response: `{"success":false,"message":"Invalid email or password"}`.

Email syntax or request-shape errors return `400 Bad Request`.

### `GET /api/auth/me`

With the login cookie: `200 OK`, the safe `user` object and the current session `csrfToken`. Without a valid logged-in session: `401 Unauthorized`, `{"success":false,"message":"Authentication required"}`.

### `POST /api/auth/logout`

Requires the login cookie and header `X-CSRF-Token: <csrfToken from login or /me>`. Success: `200 OK`, `{"success":true,"message":"Logged out"}`; the session is invalidated. A missing/invalid token returns `403 Forbidden`; missing authentication returns `401 Unauthorized`.

## Cookies, CSRF, and role checks

- Session idle timeout is 30 minutes. Session tracking is cookie-only; the session ID is renewed on successful login.
- `JSESSIONID` is HttpOnly and SameSite=Lax. Tomcat marks the cookie Secure on HTTPS requests when `Secure` is not forced in the app deployment descriptor; local HTTP remains usable. HTTPS behind a reverse proxy must be configured so Tomcat recognizes the request as secure.
- Keep the cookie jar enabled in Postman. Do not copy the session ID into application JSON or logs.
- `login` returns a session-bound 32-byte random CSRF token; `/me` returns it again. Send it in `X-CSRF-Token` on state-changing authenticated API calls, including logout. The filter checks the header with a constant-time comparison. Registration and login are public bootstrap endpoints and are exempt from this session CSRF check.
- All `/api/*` routes other than health and the required auth routes require an authenticated session. Add `@RequireRoles({"ORGANIZER"})` (or `JUDGE`, `ADMIN`, etc.) to a servlet class to require a role. The role comes from the database-backed server-side session principal; request JSON/headers cannot select a role. A missing session gets 401, an authenticated but disallowed role gets 403.
- No credentialed CORS policy is enabled. Keep a future browser frontend same-origin with the API, or design a separate explicit CORS policy and CSRF approach when its origin is known.

## Postman verification

Use the local base URL above, enable Postman's cookie jar, and create requests in this order:

1. **Register** — `POST {{baseUrl}}/api/auth/register`, JSON body shown above, using a new email. Expect 201 and `role: STUDENT`.
2. **Duplicate registration** — repeat the same request. Expect 409.
3. **Invalid input** — register with a short password (for example `short`) or malformed email. Expect 400.
4. **Wrong password** — `POST {{baseUrl}}/api/auth/login` with the registered email and a wrong password. Expect generic 401.
5. **Correct login** — post the correct email/password. Expect 200, safe user details, a `csrfToken`, and `JSESSIONID` in Postman's cookie jar. Save `csrfToken` in a Postman variable if desired.
6. **Current user** — `GET {{baseUrl}}/api/auth/me`. Keep the cookie jar enabled. Expect 200 and the safe user plus CSRF token.
7. **Logout** — `POST {{baseUrl}}/api/auth/logout`, send `X-CSRF-Token` with the token from login or `/me`; no body is required. Expect 200.
8. **After logout** — `GET {{baseUrl}}/api/auth/me`. The invalidated session should return 401.

The environment variable `baseUrl` should be `http://localhost:8080/CampusConnect` only when that is the connector/context used by your Tomcat. Change the scheme, host, port, or context to the actual deployed endpoint when it differs. For automated API checks, use a disposable test email and password that are not production credentials.

## Schema migration prerequisite

Before registration/login can succeed, run `database/06_auth_credentials.sql` manually as `CAMPUSCONNECT` while connected to `FREEPDB1`. It adds nullable credential fields and a normalized-email unique index; it does not alter existing user rows. Existing accounts remain unable to log in until a password-setting flow is designed. Do not run the Phase 3 sample-data script again.
