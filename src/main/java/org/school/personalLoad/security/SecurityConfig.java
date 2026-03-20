package org.school.personalLoad.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import javax.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final RepositoryUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authenticationProvider(authenticationProvider())
                .authorizeRequests(authorize -> authorize
                        .antMatchers("/login.html", "/auth.js", "/styles.css").permitAll()
                        .antMatchers("/api/auth/**").permitAll()
                        .antMatchers("/favicon.ico", "/error").permitAll()
                        .antMatchers("/*.js", "/*.css").permitAll()
                        .antMatchers("/ui/**", "/api/**", "/*.html", "/").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(apiAwareEntryPoint()))
                .logout(logout -> logout.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint apiAwareEntryPoint() {
        return (request, response, authException) -> {
            if (request.getRequestURI().startsWith("/api/")) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Authentication required\"}");
                return;
            }
            response.sendRedirect("/login.html");
        };
    }
}
