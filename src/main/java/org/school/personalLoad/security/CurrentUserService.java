package org.school.personalLoad.security;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.user.AppUser;
import org.school.personalLoad.user.AppUserRepository;
import org.school.personalLoad.user.RoleName;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository userRepository;

    public Optional<AppUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByUsername(authentication.getName());
    }

    public AppUser requireCurrentUser() {
        return getCurrentUser().orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    public boolean hasRole(RoleName roleName) {
        return getCurrentUser().map(AppUser::getRole).filter(roleName::equals).isPresent();
    }
}
