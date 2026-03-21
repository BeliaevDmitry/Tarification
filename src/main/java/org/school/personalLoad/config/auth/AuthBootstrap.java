package org.school.personalLoad.config.auth;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.service.auth.AppUserService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AuthBootstrap {

    private final AppUserService appUserService;

    @Bean
    public ApplicationRunner initDefaultAdminRunner() {
        return args -> appUserService.ensureDefaultAdmin();
    }
}
