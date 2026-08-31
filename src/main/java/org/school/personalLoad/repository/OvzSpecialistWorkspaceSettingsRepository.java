package org.school.personalLoad.repository;

import org.school.personalLoad.model.OvzSpecialistWorkspaceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OvzSpecialistWorkspaceSettingsRepository
        extends JpaRepository<OvzSpecialistWorkspaceSettings, Long> {
    Optional<OvzSpecialistWorkspaceSettings> findFirstByOrderByIdAsc();
}
