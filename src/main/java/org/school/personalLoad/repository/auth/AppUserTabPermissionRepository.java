package org.school.personalLoad.repository.auth;

import org.school.personalLoad.auth.AppUserTabPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppUserTabPermissionRepository extends JpaRepository<AppUserTabPermission, Long> {
    List<AppUserTabPermission> findAllByUserIdOrderByTabAsc(Long userId);
    void deleteAllByUserId(Long userId);
}
