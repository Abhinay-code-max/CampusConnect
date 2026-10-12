package com.campusconnect.filter;

import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class SecurityHeadersFilterTest {
    @Test void setsBrowserHeadersAndNoStoreForApiResponses() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/CampusConnect/api/admin/users");
        when(request.getContextPath()).thenReturn("/CampusConnect");
        when(request.isSecure()).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(response).setHeader("X-Content-Type-Options", "nosniff");
        verify(response).setHeader("X-Frame-Options", "DENY");
        verify(response).setHeader("Cache-Control", "no-store");
        verify(response, never()).setHeader(eq("Strict-Transport-Security"), anyString());
        verify(response).setHeader(eq("Content-Security-Policy"), contains("script-src 'self'"));
        verify(chain).doFilter(request, response);
    }

    @Test void addsHstsOnlyWhenRequestIsSecure() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/CampusConnect/");
        when(request.getContextPath()).thenReturn("/CampusConnect");
        when(request.isSecure()).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Strict-Transport-Security", "max-age=31536000");
    }
}
