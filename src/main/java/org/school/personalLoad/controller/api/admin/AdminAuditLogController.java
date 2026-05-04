package org.school.personalLoad.controller.api.admin;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.repository.UserActionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {
    private final UserActionLogRepository userActionLogRepository;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        Instant toInstant = to == null || to.isBlank() ? Instant.now() : Instant.parse(to);
        Instant fromInstant = from == null || from.isBlank() ? toInstant.minus(360, ChronoUnit.DAYS) : Instant.parse(from);
        Page<?> result = userActionLogRepository.findByCreatedAtBetweenAndActionTypeContainingIgnoreCaseAndUsernameContainingIgnoreCase(
                fromInstant,
                toInstant,
                action == null ? "" : action,
                user == null ? "" : user,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "number", result.getNumber(),
                "size", result.getSize()
        );
    }
}
