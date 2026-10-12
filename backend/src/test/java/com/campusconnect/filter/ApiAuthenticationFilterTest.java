package com.campusconnect.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.service.AuthService;
import com.campusconnect.controller.AdminServlet;
import com.campusconnect.util.CsrfTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

class ApiAuthenticationFilterTest {
    private ApiAuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private HttpSession session;
    private AuthService authService;

    @BeforeEach void setup() throws Exception {
        authService = mock(AuthService.class);
        filter = new ApiAuthenticationFilter(authService);
        request = mock(HttpServletRequest.class); response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class); session = mock(HttpSession.class);
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            @Override public void write(int value) { bytes.write(value); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) { }
        });
        when(request.getRequestURI()).thenReturn("/CampusConnect/api/protected");
        when(request.getContextPath()).thenReturn("/CampusConnect");
        when(request.getMethod()).thenReturn("GET");
    }

    @Test void rejectsProtectedRequestWithoutSession() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        filter.doFilter(request, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(request, response);
    }

    @Test void publicEventReadDoesNotRequireSession() throws Exception {
        when(request.getRequestURI()).thenReturn("/CampusConnect/api/events/9223372036854775807");
        when(request.getSession(false)).thenReturn(null);
        filter.doFilter(request,response,chain);
        verify(chain).doFilter(request,response);
        verify(response,never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test void resultReadIsPublicButPublicationIsEnforcedByResultService() throws Exception {
        when(request.getRequestURI()).thenReturn("/CampusConnect/api/results/events/42");
        when(request.getSession(false)).thenReturn(null);
        filter.doFilter(request,response,chain);
        verify(chain).doFilter(request,response);
        verify(response,never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test void authenticatedSessionReachesProtectedRoute() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(7, "Student", "s@example.edu", "STUDENT");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL)).thenReturn(principal);
        when(authService.refreshSessionAccount(7)).thenReturn(principal);
        filter.doFilter(request, response, chain);
        verify(request).setAttribute(ApiAuthenticationFilter.PRINCIPAL_ATTRIBUTE, principal);
        verify(chain).doFilter(request, response);
    }

    @Test void roleAnnotationReturns403ForWrongRole() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL))
                .thenReturn(new AuthPrincipal(7, "Student", "s@example.edu", "STUDENT"));
        when(authService.refreshSessionAccount(7)).thenReturn(new AuthPrincipal(7, "Student", "s@example.edu", "STUDENT"));
        HttpServletMapping mapping = mock(HttpServletMapping.class);
        when(mapping.getServletName()).thenReturn("admin");
        when(request.getHttpServletMapping()).thenReturn(mapping);
        var context = mock(jakarta.servlet.ServletContext.class);
        when(request.getServletContext()).thenReturn(context);
        ServletRegistration registration = mock(ServletRegistration.class);
        when(context.getServletRegistration("admin")).thenReturn(registration);
        when(registration.getClassName()).thenReturn(AdminServlet.class.getName());
        filter.doFilter(request, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test void permittedRoleReachesProtectedServlet() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL))
                .thenReturn(new AuthPrincipal(9, "Admin", "admin@example.edu", "ADMIN"));
        when(authService.refreshSessionAccount(9)).thenReturn(new AuthPrincipal(9, "Admin", "admin@example.edu", "ADMIN"));
        HttpServletMapping mapping = mock(HttpServletMapping.class);
        when(mapping.getServletName()).thenReturn("admin");
        when(request.getHttpServletMapping()).thenReturn(mapping);
        var context = mock(jakarta.servlet.ServletContext.class);
        when(request.getServletContext()).thenReturn(context);
        ServletRegistration registration = mock(ServletRegistration.class);
        when(context.getServletRegistration("admin")).thenReturn(registration);
        when(registration.getClassName()).thenReturn(AdminServlet.class.getName());
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test void refreshedRoleReplacesStaleSessionRoleBeforeAuthorization() throws Exception {
        AuthPrincipal old = new AuthPrincipal(9, "Admin", "admin@example.edu", "ADMIN");
        AuthPrincipal current = new AuthPrincipal(9, "Admin", "admin@example.edu", "STUDENT");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL)).thenReturn(old);
        when(authService.refreshSessionAccount(9)).thenReturn(current);
        HttpServletMapping mapping = mock(HttpServletMapping.class);
        when(mapping.getServletName()).thenReturn("admin"); when(request.getHttpServletMapping()).thenReturn(mapping);
        var context = mock(jakarta.servlet.ServletContext.class); when(request.getServletContext()).thenReturn(context);
        ServletRegistration registration = mock(ServletRegistration.class); when(context.getServletRegistration("admin")).thenReturn(registration);
        when(registration.getClassName()).thenReturn(AdminServlet.class.getName());
        filter.doFilter(request,response,chain);
        verify(session).setAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL,current);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain,never()).doFilter(request,response);
    }

    @Test void inactiveAccountInvalidatesExistingSession() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL)).thenReturn(new AuthPrincipal(7,"Student","s@example.edu","STUDENT"));
        when(authService.refreshSessionAccount(7)).thenReturn(null);
        filter.doFilter(request,response,chain);
        verify(session).invalidate(); verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain,never()).doFilter(request,response);
    }

    @Test void stateChangingRequestRequiresSessionCsrfToken() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL))
                .thenReturn(new AuthPrincipal(7, "Student", "s@example.edu", "STUDENT"));
        when(authService.refreshSessionAccount(7)).thenReturn(new AuthPrincipal(7, "Student", "s@example.edu", "STUDENT"));
        when(session.getAttribute(ApiAuthenticationFilter.SESSION_CSRF)).thenReturn(CsrfTokens.create());
        filter.doFilter(request, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test void adminMutationRequiresCsrfEvenForAnAdministrator() throws Exception {
        when(request.getMethod()).thenReturn("PUT"); when(request.getSession(false)).thenReturn(session);
        AuthPrincipal admin=new AuthPrincipal(9,"Admin","admin@example.edu","ADMIN");
        when(session.getAttribute(ApiAuthenticationFilter.SESSION_PRINCIPAL)).thenReturn(admin);
        when(authService.refreshSessionAccount(9)).thenReturn(admin);
        HttpServletMapping mapping=mock(HttpServletMapping.class);when(mapping.getServletName()).thenReturn("admin");when(request.getHttpServletMapping()).thenReturn(mapping);
        var context=mock(jakarta.servlet.ServletContext.class);when(request.getServletContext()).thenReturn(context);
        ServletRegistration registration=mock(ServletRegistration.class);when(context.getServletRegistration("admin")).thenReturn(registration);
        when(registration.getClassName()).thenReturn(AdminServlet.class.getName());
        filter.doFilter(request,response,chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);verify(chain,never()).doFilter(request,response);
    }

}
