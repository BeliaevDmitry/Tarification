package org.school.personalLoad.repository;

import org.school.personalLoad.model.AdmissionAccessRole;
import org.school.personalLoad.model.AdmissionRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdmissionRoleAssignmentRepository extends JpaRepository<AdmissionRoleAssignment, Long> {
    List<AdmissionRoleAssignment> findAllByOrderByRoleAscUserIdAsc();
    boolean existsByUserIdAndRole(Long userId, AdmissionAccessRole role);
}
