package org.school.personalLoad.security;

import org.school.personalLoad.user.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.List;

public class AuthenticatedUser extends User {
    private final Long id;
    private final String fullName;
    private final String email;
    private final String role;

    public AuthenticatedUser(AppUser user) {
        super(user.getUsername(), user.getPassword(), user.isEnabled(), true, true, true, authorities(user));
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.role = user.getRole().name();
    }

    private static Collection<? extends GrantedAuthority> authorities(AppUser user) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
