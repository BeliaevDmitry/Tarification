package org.school.personalLoad.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.user.AppUser;
import org.school.personalLoad.user.AppUserRepository;
import org.school.personalLoad.user.RoleName;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(properties.getUsername()).isPresent()) {
            return;
        }
        String generated = properties.getPassword();
        if (generated == null || generated.isBlank()) {
            generated = generatePassword(12);
            log.warn("Bootstrap admin password generated for username '{}': {}", properties.getUsername(), generated);
        }
        AppUser user = new AppUser();
        user.setUsername(properties.getUsername());
        user.setPassword(passwordEncoder.encode(generated));
        user.setEmail(properties.getEmail());
        user.setFullName(properties.getFullName());
        user.setRole(RoleName.ADMIN);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("Bootstrap admin '{}' created", properties.getUsername());
    }

    private String generatePassword(int length) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        return builder.toString();
    }
}
