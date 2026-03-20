package org.school.personalLoad.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.user.AppUser;
import org.school.personalLoad.user.AppUserRepository;
import org.school.personalLoad.user.RoleName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "DB_PASSWORD=test",
        "app.bootstrap-admin.password=adminpass"
})
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        appUserRepository.deleteAll();
        AppUser user = new AppUser();
        user.setUsername("tester");
        user.setPassword(passwordEncoder.encode("secret"));
        user.setRole(RoleName.ADMIN);
        user.setEnabled(true);
        appUserRepository.save(user);
    }

    @Test
    void loginAndMeReturnAuthenticatedUser() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("username", "tester");
            put("password", "secret");
        }});

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"))
                .andReturn();

        mockMvc.perform(get("/api/auth/me").session(loginResult.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginFailsWithBadCredentials() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("username", "tester");
            put("password", "wrong");
        }});

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
