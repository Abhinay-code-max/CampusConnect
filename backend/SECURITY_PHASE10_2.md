# Phase 10.2 security hardening notes

## Request and authentication protections

- JSON request bodies are limited to 1 MiB. Oversized JSON requests receive HTTP 413.
- Login counts failed attempts per normalized account and client address. Ten failures for one account or forty failures from one address within fifteen minutes return HTTP 429 with `Retry-After`.
- The limiter is process-local and bounded. Production should also apply rate limiting at the trusted reverse proxy or load balancer so multiple Tomcat instances share a limit. Do not trust arbitrary forwarded client-IP headers; configure proxy trust only for known proxy addresses.
- Session cookies remain HttpOnly and SameSite=Lax. The 30-minute idle timeout, session-ID renewal at login, database-backed role/status refresh, and CSRF checks remain in place.

## Browser response protections

The application applies a same-origin Content Security Policy, `nosniff`, clickjacking protection, a strict referrer policy, and a restrictive Permissions Policy. API responses use `Cache-Control: no-store`. HSTS is sent only when `request.isSecure()` is true.

## Tomcat production settings

For HTTPS-only production, configure Tomcat or the trusted front proxy so requests reach the application as secure. Set the JVM system property `-Dcampusconnect.session.cookie.secure=true` for the Tomcat service. Leave this property unset for local HTTP development. If TLS terminates at a proxy, configure Tomcat's `RemoteIpValve` (or equivalent) with only the actual trusted proxy addresses; never trust client-supplied forwarded headers from arbitrary sources.

The inspected local Tomcat has HTTP on port 8080 and an SSL-enabled connector on 8443. The application does not change connector configuration, enable redirects, or restart Tomcat. Before production, verify that the 8443 certificate is valid for the public hostname, the keystore and its password are protected outside the WAR and source tree, plain HTTP is redirected or disabled at the edge, and only trusted proxy addresses are accepted.

## Security review scope and remaining operational controls

The servlet mappings enforce STUDENT, ORGANIZER, JUDGE, and ADMIN roles; protected requests reload account role and active state from Oracle. Event ownership, judge assignment, result visibility, and administrative mutations are enforced in service/DAO paths. JDBC request values use prepared statements, and dynamic sort columns come from fixed allowlists. No upload or download endpoints were found. CORS is not enabled; the frontend uses same-origin requests.

No database records, roles, grants, credentials, backup files, or deployed WARs were changed. Local/deployed browser checks require deployment of this build, which is intentionally not performed as part of Phase 10.2.
