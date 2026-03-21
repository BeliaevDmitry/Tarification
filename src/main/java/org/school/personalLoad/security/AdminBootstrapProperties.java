package org.school.personalLoad.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public class AdminBootstrapProperties {
    private String username = "admin";
    private String password;
    private String email;
    private String fullName = "System Administrator";
}
