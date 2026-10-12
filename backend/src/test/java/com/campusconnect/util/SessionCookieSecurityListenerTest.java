package com.campusconnect.util;

import static org.mockito.Mockito.*;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.SessionCookieConfig;
import org.junit.jupiter.api.Test;

class SessionCookieSecurityListenerTest {
    @Test void enablesSecureSessionCookiesWhenProductionPropertyIsTrue() {
        String prior = System.getProperty(SessionCookieSecurityListener.SECURE_COOKIE_PROPERTY);
        try {
            System.setProperty(SessionCookieSecurityListener.SECURE_COOKIE_PROPERTY, "true");
            ServletContext context = mock(ServletContext.class);
            SessionCookieConfig cookie = mock(SessionCookieConfig.class);
            when(context.getSessionCookieConfig()).thenReturn(cookie);
            new SessionCookieSecurityListener().contextInitialized(new ServletContextEvent(context));
            verify(cookie).setSecure(true);
        } finally {
            if (prior == null) System.clearProperty(SessionCookieSecurityListener.SECURE_COOKIE_PROPERTY);
            else System.setProperty(SessionCookieSecurityListener.SECURE_COOKIE_PROPERTY, prior);
        }
    }
}
