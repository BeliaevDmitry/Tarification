package org.school.personalLoad.masterfot;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FotControllerTest {
    @Test void returnsReadableErrorInsteadOfGenericServerFailure() throws Exception {
        var service = mock(FotService.class); var years = mock(AcademicYearService.class);
        when(years.resolveRequestedOrDefault("2026/2027")).thenReturn("2026/2027");
        when(service.options("2026/2027")).thenThrow(new IllegalArgumentException("В системе нет учебного плана"));
        var mvc = MockMvcBuilders.standaloneSetup(new FotController(service,years))
                .setControllerAdvice(new FotExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules())).build();
        mvc.perform(get("/api/master-fot/options").param("academicYear","2026/2027"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("В системе нет учебного плана"));
    }
}
