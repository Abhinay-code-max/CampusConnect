package com.campusconnect.controller;

import com.campusconnect.exception.DuplicateEmailException;
import com.campusconnect.exception.InvalidCredentialsException;
import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.filter.ApiAuthenticationFilter;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.LoginRequest;
import com.campusconnect.model.RegisterRequest;
import com.campusconnect.service.AuthService;
import com.campusconnect.service.AuthInputValidator;
import com.campusconnect.service.LoginRateLimiter;
import com.campusconnect.util.ApiResponses;
import com.campusconnect.util.CsrfTokens;
import com.campusconnect.util.Json;
import com.campusconnect.util.SafeDiagnostics;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet("/api/auth/*")
public final class AuthServlet extends HttpServlet {
    private final AuthService authService = new AuthService();
    private final LoginRateLimiter loginRateLimiter = LoginRateLimiter.instance();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        try {
            if ("/register".equals(path)) {
                AuthPrincipal user = authService.register(Json.read(request, RegisterRequest.class));
                response.setHeader("Cache-Control", "no-store");
                Json.write(response, HttpServletResponse.SC_CREATED, successWithUser(user));
            } else if ("/login".equals(path)) {
                LoginRequest login = Json.read(request, LoginRequest.class);
                String normalizedEmail = AuthInputValidator.normalizeEmail(login.getEmail());
                String remoteAddress = request.getRemoteAddr();
                long retryAfter = loginRateLimiter.retryAfterSeconds(remoteAddress, normalizedEmail);
                if (retryAfter > 0) {
                    response.setHeader("Retry-After", Long.toString(retryAfter));
                    ApiResponses.error(response, 429, "Too many login attempts. Try again later.");
                    return;
                }
                AuthPrincipal user;
                try {
                    user = authService.login(login);
                    loginRateLimiter.recordSuccess(normalizedEmail);
                } catch (InvalidCredentialsException invalid) {
                    loginRateLimiter.recordFailure(remoteAddress, normalizedEmail);
                    throw invalid;
                }
                HttpSession session = request.getSession(true);
                request.changeSessionId();
                session = request.getSession(false);
                session.setMaxInactiveInterval(30 * 60);
                String csrfToken = CsrfTokens.create();
                session.setAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL, user);
                session.setAttribute(ApiAuthenticationFilter.SESSION_CSRF, csrfToken);
                Map<String, Object> result = successWithUser(user);
                result.put("csrfToken", csrfToken);
                response.setHeader("Cache-Control", "no-store");
                Json.write(response, HttpServletResponse.SC_OK, result);
            } else if ("/logout".equals(path)) {
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setHeader("Cache-Control", "no-store");
                Json.write(response, HttpServletResponse.SC_OK,
                        Map.of("success", true, "message", "Logged out"));
            } else {
                ApiResponses.error(response, HttpServletResponse.SC_NOT_FOUND, "Not found");
            }
        } catch (ValidationException e) {
            ApiResponses.error(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (ApiException e) {
            ApiResponses.error(response, e.getStatus(), e.getMessage());
        } catch (DuplicateEmailException e) {
            ApiResponses.error(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (InvalidCredentialsException e) {
            ApiResponses.error(response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        } catch (IOException e) {
            ApiResponses.error(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON request body");
        } catch (Exception e) {
            log("Authentication request failed (" + SafeDiagnostics.describe(e) + ")");
            ApiResponses.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Request could not be processed");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/me".equals(request.getPathInfo())) {
            ApiResponses.error(response, HttpServletResponse.SC_NOT_FOUND, "Not found");
            return;
        }
        HttpSession session = request.getSession(false);
        AuthPrincipal user = session == null ? null
                : (AuthPrincipal) session.getAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL);
        if (user == null) {
            ApiResponses.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }
        try {
            user = authService.refreshSessionAccount(user.userId());
            if (user == null) {
                session.invalidate();
                ApiResponses.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                return;
            }
            session.setAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL, user);
        } catch (RuntimeException e) {
            log("Session account refresh failed (" + SafeDiagnostics.describe(e) + ")");
            ApiResponses.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Request could not be processed");
            return;
        }
        Map<String, Object> result = successWithUser(user);
        result.put("csrfToken", session.getAttribute(ApiAuthenticationFilter.SESSION_CSRF));
        response.setHeader("Cache-Control", "no-store");
        Json.write(response, HttpServletResponse.SC_OK, result);
    }

    private static Map<String, Object> successWithUser(AuthPrincipal user) {
        Map<String, Object> safeUser = new LinkedHashMap<>();
        safeUser.put("userId", user.userId());
        safeUser.put("displayName", user.displayName());
        safeUser.put("email", user.email());
        safeUser.put("role", user.role());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("user", safeUser);
        return result;
    }
}
