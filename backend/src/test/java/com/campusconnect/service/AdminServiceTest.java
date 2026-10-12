package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.campusconnect.dao.AdminDao;
import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.AuthPrincipal;
import org.junit.jupiter.api.Test;

class AdminServiceTest {
    private final AdminDao dao=mock(AdminDao.class);
    private final AdminService service=new AdminService(dao,mock(EventService.class));
    private final AuthPrincipal admin=new AuthPrincipal(8,"Admin","a@example.invalid","ADMIN");
    private final AuthPrincipal student=new AuthPrincipal(9,"Student","s@example.invalid","STUDENT");

    @Test void requiresAdminAtServiceBoundary(){assertEquals(403,assertThrows(ApiException.class,()->service.dashboard(student)).getStatus());assertEquals(401,assertThrows(ApiException.class,()->service.dashboard(null)).getStatus());}
    @Test void validatesRoleActiveAndPaginationInputsBeforeDao(){
        assertThrows(ValidationException.class,()->service.role(admin,12,"ROOT"));
        assertThrows(ValidationException.class,()->service.users(admin,null,"ROOT",null,null,null,null,null));
        assertThrows(ValidationException.class,()->service.users(admin,null,null,"maybe",null,null,null,null));
        assertThrows(ValidationException.class,()->service.users(admin,null,null,null,null,null,"0",null));
        assertThrows(ValidationException.class,()->service.users(admin,null,null,null,null,null,null,"101"));
        verifyNoInteractions(dao);
    }
}
