package org.school.personalLoad.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    Page<AppUser> findAllByRole(RoleName role, Pageable pageable);
}
