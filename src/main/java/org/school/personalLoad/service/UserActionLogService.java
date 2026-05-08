package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.UserActionLog;
import org.school.personalLoad.repository.UserActionLogRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class UserActionLogService {
    private final UserActionLogRepository userActionLogRepository;

    public void save(UserActionLog logEntry) {
        userActionLogRepository.save(logEntry);
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void purgeOldLogs() {
        userActionLogRepository.deleteByCreatedAtBefore(Instant.now().minus(360, ChronoUnit.DAYS));
    }
}
