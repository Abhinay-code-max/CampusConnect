package com.campusconnect.service;

import com.campusconnect.exception.ApiException;
import com.campusconnect.model.AuthPrincipal;

public final class EventAccessPolicy {
    private EventAccessPolicy() { }
    public static boolean isManager(AuthPrincipal principal) {
        return principal!=null&&("ORGANIZER".equals(principal.role())||"ADMIN".equals(principal.role()));
    }
    public static boolean canManage(AuthPrincipal principal,long ownerId) {
        return principal!=null&&("ADMIN".equals(principal.role())||("ORGANIZER".equals(principal.role())&&principal.userId()==ownerId));
    }
    public static boolean canManage(long actorId,boolean admin,long ownerId) { return admin||actorId==ownerId; }
    public static void requireManager(AuthPrincipal principal) {
        if(principal==null)throw new ApiException(401,"Authentication required");
        if(!isManager(principal))throw new ApiException(403,"Forbidden");
    }
}
