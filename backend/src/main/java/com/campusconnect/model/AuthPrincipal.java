package com.campusconnect.model;

/** Safe, server-side session identity. Never contains password credentials. */
public record AuthPrincipal(long userId, String displayName, String email, String role)
        implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
}
