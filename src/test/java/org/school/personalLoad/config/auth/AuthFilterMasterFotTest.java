package org.school.personalLoad.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.auth.*;
import org.school.personalLoad.masterfot.*;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.auth.AppUserService;
import org.springframework.mock.web.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthFilterMasterFotTest {
    private SessionUser user(boolean view,boolean edit,boolean upload) {
        return new SessionUser(42L,"methodist","Методист",null,null,UserRole.METHODIST,true,true,true,null,false,new LinkedHashSet<>(),
                List.of(new TabPermissionSnapshot(AppTab.LOAD_MASTER_FOT,view,edit,upload,false)));
    }
    private void check(String method,String path,SessionUser user,boolean allowed) throws Exception {
        AppUserService users = mock(AppUserService.class); when(users.findSessionUser(42L)).thenReturn(user);
        var req = new MockHttpServletRequest(method,path); req.getSession(true).setAttribute(SessionUser.SESSION_KEY,user);
        var response = new MockHttpServletResponse(); var passed = new AtomicBoolean();
        new AuthFilter(new ObjectMapper().findAndRegisterModules(),users).doFilter(req,response,(r,s) -> passed.set(true));
        assertThat(passed.get()).isEqualTo(allowed);
        assertThat(response.getStatus()).isEqualTo(allowed ? 200 : path.endsWith(".html") ? 302 : 403);
    }
    @Test void dedicatedViewPermissionProtectsPageAndAllReads() throws Exception {
        for (String path:List.of("/master-fot.html","/api/master-fot","/api/master-fot/options","/api/master-fot/batches/1")) {
            check("GET",path,user(false,false,false),false); check("GET",path,user(true,false,false),true);
        }
    }
    @Test void readOnlyUserCannotEditOrUpload() throws Exception {
        check("PATCH","/api/master-fot/issues/test",user(true,false,false),false);
        check("PUT","/api/master-fot/mappings",user(true,false,false),false);
        check("POST","/api/master-fot/import",user(true,false,false),false);
        check("PATCH","/api/master-fot/issues/test",user(true,true,false),true);
    }
    @Test void importRequiresSeparateImportPermissionEvenWithEdit() {
        var service = mock(FotService.class); var controller = new FotController(service,mock(AcademicYearService.class));
        var request = new MockHttpServletRequest(); request.getSession(true).setAttribute(SessionUser.SESSION_KEY,user(true,true,false));
        assertThatThrownBy(() -> controller.upload("2026/2027",new MockMultipartFile("file",new byte[]{1}),request)).isInstanceOf(AuthExceptions.ForbiddenException.class);
        verifyNoInteractions(service);
    }
}
