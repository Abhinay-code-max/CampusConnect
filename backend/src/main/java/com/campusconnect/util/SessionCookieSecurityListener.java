package com.campusconnect.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Allows production to require Secure session cookies without breaking local HTTP development. */
@WebListener
public final class SessionCookieSecurityListener implements ServletContextListener {
    public static final String SECURE_COOKIE_PROPERTY = "campusconnect.session.cookie.secure";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        String configured = System.getProperty(SECURE_COOKIE_PROPERTY);
        if (configured == null || configured.isBlank()) return;
        if (!"true".equalsIgnoreCase(configured) && !"false".equalsIgnoreCase(configured)) {
            throw new IllegalStateException(SECURE_COOKIE_PROPERTY + " must be true or false");
        }
        event.getServletContext().getSessionCookieConfig().setSecure(Boolean.parseBoolean(configured));
    }
}
