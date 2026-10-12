package com.campusconnect.filter;

import com.campusconnect.model.AuthPrincipal;
import java.util.Arrays;

public final class RoleAuthorizer {
    private RoleAuthorizer() { }
    public static boolean allows(AuthPrincipal principal, String[] requiredRoles) {
        return principal != null && Arrays.stream(requiredRoles).anyMatch(principal.role()::equals);
    }
}
