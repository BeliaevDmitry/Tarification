package org.school.personalLoad.service;

import org.school.personalLoad.model.ApplicationTimeSettings;
import org.school.personalLoad.repository.ApplicationTimeSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
public class ApplicationTimeService {
    static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final long SETTINGS_ID = 1L;

    private final ApplicationTimeSettingsRepository repository;
    private final Clock systemClock;

    public ApplicationTimeService(ApplicationTimeSettingsRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ApplicationTimeService(ApplicationTimeSettingsRepository repository, Clock systemClock) {
        this.repository = repository;
        this.systemClock = systemClock;
    }

    @Transactional(readOnly = true)
    public TimeView current() {
        Instant systemInstant = systemClock.instant();
        ApplicationTimeSettings settings = repository.findById(SETTINGS_ID).orElse(null);
        long offsetMillis = settings == null ? 0L : settings.getOffsetMillis();
        LocalDateTime current = local(systemInstant.plusMillis(offsetMillis));
        return new TimeView(current, local(systemInstant), ZONE.getId(), offsetMillis,
                settings != null && offsetMillis != 0L,
                settings == null ? null : settings.getUpdatedAt(),
                settings == null ? null : settings.getUpdatedBy());
    }

    @Transactional
    public TimeView setCurrent(LocalDateTime requested, String user) {
        if (requested == null) throw new IllegalArgumentException("Укажите текущие дату и время");
        if (requested.getYear() < 2000 || requested.getYear() > 2100)
            throw new IllegalArgumentException("Год должен быть в диапазоне от 2000 до 2100");

        Instant systemInstant = systemClock.instant();
        Instant requestedInstant = requested.atZone(ZONE).toInstant();
        long offsetMillis = ChronoUnit.MILLIS.between(systemInstant, requestedInstant);
        ApplicationTimeSettings settings = repository.findById(SETTINGS_ID).orElseGet(ApplicationTimeSettings::new);
        settings.setOffsetMillis(offsetMillis);
        settings.setZoneId(ZONE.getId());
        settings.setUpdatedAt(local(systemInstant.plusMillis(offsetMillis)));
        settings.setUpdatedBy(user == null ? "" : user.trim());
        repository.save(settings);
        return current();
    }

    private LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE).withNano(0);
    }

    public record TimeView(LocalDateTime currentDateTime, LocalDateTime systemDateTime,
                           String zoneId, long offsetMillis, boolean adjusted,
                           LocalDateTime updatedAt, String updatedBy) {
    }
}
