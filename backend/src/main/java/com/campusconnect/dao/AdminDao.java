package com.campusconnect.dao;

import java.util.List;
import java.util.Map;

public interface AdminDao {
    Map<String, Object> listUsers(String query, String role, Boolean active, String sort, String direction, int page, int size);
    Map<String, Object> findUser(long userId);
    Map<String, Object> changeRole(long actorId, long targetId, String role);
    Map<String, Object> changeActive(long actorId, long targetId, boolean active);
    Map<String, Object> dashboard();
    Map<String, Object> listRegistrations(Long eventId, String status, int page, int size);
    Map<String, Object> listAudit(Long actorId, Long targetId, String action, int page, int size);
}
