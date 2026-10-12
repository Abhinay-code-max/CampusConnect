package com.campusconnect.model;

public record UserAccount(long userId, String displayName, String email, String role, boolean active,
                          byte[] passwordSalt, byte[] passwordHash, int passwordIterations) {
    public boolean hasPasswordCredentials() {
        return passwordSalt != null && passwordHash != null && passwordIterations >= 600_000;
    }
}
