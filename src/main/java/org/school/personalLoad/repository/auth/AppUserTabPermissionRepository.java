package org.school.personalLoad.repository.auth;

import org.school.personalLoad.auth.AppUserTabPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.school.personalLoad.auth.AppTab;

@Repository
public interface AppUserTabPermissionRepository extends JpaRepository<AppUserTabPermission, Long> {
    List<AppUserTabPermission> findAllByUserIdOrderByTabAsc(Long userId);
    List<AppUserTabPermission> findAllByTabAndCanExportTrue(AppTab tab);
    void deleteAllByUserId(Long userId);
}
