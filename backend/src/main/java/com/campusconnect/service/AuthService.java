package com.campusconnect.service;

import com.campusconnect.dao.AuthDao;
import com.campusconnect.dao.JdbcAuthDao;
import com.campusconnect.exception.InvalidCredentialsException;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.LoginRequest;
import com.campusconnect.model.RegisterRequest;
import com.campusconnect.model.UserAccount;
import com.campusconnect.util.PasswordHasher;

public final class AuthService {
    private static final PasswordHasher.Hash DUMMY_CREDENTIALS = PasswordHasher.hash("not-a-real-user-password");
    private final AuthDao dao;

    public AuthService() { this(new JdbcAuthDao()); }
    public AuthService(AuthDao dao) { this.dao = dao; }

    public AuthPrincipal register(RegisterRequest request) {
        AuthInputValidator.ValidRegistration valid = AuthInputValidator.validate(request);
        UserAccount account = dao.register(valid.displayName(), valid.email(), PasswordHasher.hash(valid.password()));
        return principal(account);
    }

    public AuthPrincipal login(LoginRequest request) {
        AuthInputValidator.ValidLogin valid = AuthInputValidator.validate(request);
        UserAccount account = dao.findByNormalizedEmail(valid.email());
        boolean credentialMatch;
        if (account != null && account.hasPasswordCredentials()) {
            credentialMatch = PasswordHasher.verify(valid.password(), account.passwordSalt(),
                    account.passwordHash(), account.passwordIterations());
        } else {
            credentialMatch = PasswordHasher.verify(valid.password(), DUMMY_CREDENTIALS.salt(),
                    DUMMY_CREDENTIALS.value(), DUMMY_CREDENTIALS.iterations());
        }
        if (!credentialMatch || account == null || !account.active() || !account.hasPasswordCredentials())
            throw new InvalidCredentialsException();
        return principal(account);
    }

    /** Reloads trusted role and active state on every protected request. */
    public AuthPrincipal refreshSessionAccount(long userId) {
        UserAccount account = dao.findById(userId);
        return account == null || !account.active() ? null : principal(account);
    }

    private static AuthPrincipal principal(UserAccount account) {
        return new AuthPrincipal(account.userId(), account.displayName(), account.email(), account.role());
    }
}
