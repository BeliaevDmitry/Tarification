package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.ApplicationTimeSettings;
import org.school.personalLoad.repository.ApplicationTimeSettingsRepository;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationTimeServiceTest {
    private static final Instant SYSTEM_INSTANT = Instant.parse("2026-09-07T09:00:00Z");

    @Test
    void springCreatesServiceWithRepositoryConstructor() {
        ApplicationTimeSettingsRepository repository = mock(ApplicationTimeSettingsRepository.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ApplicationTimeSettingsRepository.class, () -> repository);
            context.register(ApplicationTimeService.class);
            context.refresh();

            assertThat(context.getBean(ApplicationTimeService.class)).isNotNull();
        }
    }

    @Test
    void defaultsToExactMoscowTime() {
        ApplicationTimeSettingsRepository repository = mock(ApplicationTimeSettingsRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        ApplicationTimeService service = new ApplicationTimeService(repository,
                Clock.fixed(SYSTEM_INSTANT, ZoneOffset.UTC));

        ApplicationTimeService.TimeView view = service.current();

        assertThat(view.currentDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 7, 12, 0));
        assertThat(view.systemDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 7, 12, 0));
        assertThat(view.zoneId()).isEqualTo("Europe/Moscow");
        assertThat(view.adjusted()).isFalse();
    }

    @Test
    void manualTimeOffsetIsStoredAndSurvivesServiceRestart() {
        ApplicationTimeSettingsRepository repository = mock(ApplicationTimeSettingsRepository.class);
        AtomicReference<ApplicationTimeSettings> stored = new AtomicReference<>();
        when(repository.findById(1L)).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.save(any())).thenAnswer(call -> {
            ApplicationTimeSettings value = call.getArgument(0);
            stored.set(value);
            return value;
        });
        Clock clock = Clock.fixed(SYSTEM_INSTANT, ZoneOffset.UTC);
        LocalDateTime requested = LocalDateTime.of(2026, 9, 7, 15, 30, 45);

        ApplicationTimeService.TimeView saved = new ApplicationTimeService(repository, clock)
                .setCurrent(requested, "Администратор");
        ApplicationTimeService.TimeView afterRestart = new ApplicationTimeService(repository, clock).current();

        assertThat(saved.currentDateTime()).isEqualTo(requested);
        assertThat(afterRestart.currentDateTime()).isEqualTo(requested);
        assertThat(afterRestart.offsetMillis()).isEqualTo(Duration.ofHours(3).plusMinutes(30).plusSeconds(45).toMillis());
        assertThat(afterRestart.adjusted()).isTrue();
        assertThat(afterRestart.updatedBy()).isEqualTo("Администратор");
    }
}
