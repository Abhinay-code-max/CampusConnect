package com.campusconnect.filter;

import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.service.AuthService;
import com.campusconnect.util.ApiResponses;
import com.campusconnect.util.CsrfTokens;
import com.campusconnect.util.Json;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

@WebFilter(urlPatterns = "/api/*")
public final class ApiAuthenticationFilter implements Filter {
    private final AuthService authService;
    public static final String PRINCIPAL_ATTRIBUTE = AuthPrincipal.class.getName();
    public static final String SESSION_PRINCIPAL = "campusconnect.auth.principal";
    public static final String SESSION_CSRF = "campusconnect.auth.csrf";

    public ApiAuthenticationFilter() { this(new AuthService()); }
    ApiAuthenticationFilter(AuthService authService) { this.authService = authService; }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isPublic(path) || isPublicEventRead(path, request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        AuthPrincipal principal = session == null ? null : (AuthPrincipal) session.getAttribute(SESSION_PRINCIPAL);
        if (principal == null) {
            ApiResponses.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }
        try {
            AuthPrincipal refreshed = authService.refreshSessionAccount(principal.userId());
            if (refreshed == null) {
                session.invalidate();
                ApiResponses.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                return;
            }
            principal = refreshed;
            session.setAttribute(SESSION_PRINCIPAL, principal);
        } catch (RuntimeException e) {
            jakarta.servlet.ServletContext context = request.getServletContext();
            context.log("Session account refresh failed", e);
            ApiResponses.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Request could not be processed");
            return;
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);

        if (isStateChanging(request.getMethod())) {
            String expected = (String) session.getAttribute(SESSION_CSRF);
            if (!CsrfTokens.matches(expected, request.getHeader("X-CSRF-Token"))) {
                ApiResponses.error(response, HttpServletResponse.SC_FORBIDDEN, "CSRF token missing or invalid");
                return;
            }
        }

        String[] roles = requiredRoles(request);
        if (roles != null && !RoleAuthorizer.allows(principal, roles)) {
            ApiResponses.error(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isPublic(String path) {
        return path.equals("/api/health") || path.equals("/api/health/db")
                || path.equals("/api/auth/register") || path.equals("/api/auth/login")
                || path.equals("/api/auth/me");
    }

    private static boolean isPublicEventRead(String path, String method) {
        return "GET".equalsIgnoreCase(method)
                && (path.equals("/api/events") || path.startsWith("/api/events/")
                || path.startsWith("/api/results/events/"));
    }

    private static boolean isStateChanging(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    private static String[] requiredRoles(HttpServletRequest request) {
        HttpServletMapping mapping = request.getHttpServletMapping();
        if (mapping == null || mapping.getServletName() == null) return null;
        ServletRegistration registration = request.getServletContext().getServletRegistration(mapping.getServletName());
        if (registration == null) return null;
        try {
            Class<?> servletClass = Class.forName(registration.getClassName());
            RequireRoles requirement = servletClass.getAnnotation(RequireRoles.class);
            return requirement == null ? null : requirement.value();
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("Cannot inspect protected servlet role policy", e);
        }
    }
}
