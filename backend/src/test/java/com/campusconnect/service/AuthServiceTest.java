package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.campusconnect.dao.AuthDao;
import com.campusconnect.exception.DuplicateEmailException;
import com.campusconnect.exception.InvalidCredentialsException;
import com.campusconnect.model.LoginRequest;
import com.campusconnect.model.RegisterRequest;
import com.campusconnect.model.UserAccount;
import com.campusconnect.util.PasswordHasher;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    @Test void duplicateRegistrationIsPropagatedAsConflictException() {
        AuthDao dao = mock(AuthDao.class);
        when(dao.register(anyString(), anyString(), any())).thenThrow(new DuplicateEmailException());
        AuthService service = new AuthService(dao);
        RegisterRequest request = new RegisterRequest();
        request.setDisplayName("Student"); request.setEmail("s@example.edu");
        request.setPassword("a long passphrase 2026");
        assertThrows(DuplicateEmailException.class, () -> service.register(request));
    }

    @Test void inactiveAccountsAndWrongPasswordsCannotLogin() {
        PasswordHasher.Hash credentials = PasswordHasher.hash("correct horse battery staple");
        AuthDao dao = mock(AuthDao.class);
        when(dao.findByNormalizedEmail("student@example.edu")).thenReturn(new UserAccount(1, "Student",
                "student@example.edu", "STUDENT", false, credentials.salt(), credentials.value(), credentials.iterations()));
        AuthService service = new AuthService(dao);
        LoginRequest request = new LoginRequest(); request.setEmail("student@example.edu");
        request.setPassword("wrong password here");
        assertThrows(InvalidCredentialsException.class, () -> service.login(request));
    }
}
