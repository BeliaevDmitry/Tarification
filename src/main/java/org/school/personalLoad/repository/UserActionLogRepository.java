package org.school.personalLoad.repository;

import org.school.personalLoad.model.UserActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface UserActionLogRepository extends JpaRepository<UserActionLog, Long> {
    Page<UserActionLog> findByCreatedAtBetweenAndActionTypeContainingIgnoreCaseAndUsernameContainingIgnoreCase(
            Instant from,
            Instant to,
            String actionType,
            String username,
            Pageable pageable
    );

    void deleteByCreatedAtBefore(Instant threshold);
}
