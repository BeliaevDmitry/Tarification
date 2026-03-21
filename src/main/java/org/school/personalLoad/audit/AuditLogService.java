package org.school.personalLoad.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public Page<AuditLog> findAll(Long userId,
                                  String username,
                                  ActionType action,
                                  String entityType,
                                  LocalDateTime from,
                                  LocalDateTime to,
                                  Pageable pageable) {
        Specification<AuditLog> specification = Specification.where(null);
        if (userId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (username != null && !username.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
        }
        if (action != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (entityType != null && !entityType.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (from != null) {
            specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to));
        }
        return auditLogRepository.findAll(specification, pageable);
    }

    public AuditLog findById(Long id) {
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Audit log not found: " + id));
    }
}
