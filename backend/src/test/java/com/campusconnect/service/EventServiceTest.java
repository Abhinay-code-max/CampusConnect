package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.campusconnect.dao.EventDao;
import com.campusconnect.exception.ApiException;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.EventData;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EventServiceTest {
    @Test void hidesDraftReturnedByDao(){EventDao dao=mock(EventDao.class);when(dao.findPublic(9)).thenReturn(
            new EventData(9,"X","X",null,1,"TEAM","DRAFT",OffsetDateTime.now(),OffsetDateTime.now(),OffsetDateTime.now(),OffsetDateTime.now(),null,null,OffsetDateTime.now(),0,0,false));
        assertEquals(404,assertThrows(ApiException.class,()->new EventService(dao).findPublic(9)).getStatus());}
    @Test void rejectsUnauthenticatedManagement(){EventService service=new EventService(mock(EventDao.class));
        assertEquals(401,assertThrows(ApiException.class,()->service.listManaged(null,null)).getStatus());}
}
