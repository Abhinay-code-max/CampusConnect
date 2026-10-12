package com.campusconnect.dao;

import com.campusconnect.model.UserAccount;
import com.campusconnect.util.PasswordHasher;

public interface AuthDao {
    UserAccount register(String displayName, String email, PasswordHasher.Hash credentials);
    UserAccount findByNormalizedEmail(String email);
    UserAccount findById(long userId);
}
