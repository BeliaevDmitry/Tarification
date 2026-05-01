package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ServiceMemoSettingsDto;
import org.school.personalLoad.model.ServiceMemoSettings;
import org.school.personalLoad.repository.ServiceMemoSettingsRepository;
import org.school.personalLoad.service.ServiceMemoSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceMemoSettingsServiceImpl implements ServiceMemoSettingsService {
    private final ServiceMemoSettingsRepository repository;

    @Override
    @Transactional(readOnly = true)
    public ServiceMemoSettingsDto get() {
        ServiceMemoSettings settings = current();
        return new ServiceMemoSettingsDto(settings.getDirectorTitle(), settings.getDirectorName());
    }

    @Override
    @Transactional
    public ServiceMemoSettingsDto update(ServiceMemoSettingsDto dto) {
        ServiceMemoSettings settings = current();
        settings.setDirectorTitle(normalize(dto.directorTitle(), settings.getDirectorTitle()));
        settings.setDirectorName(normalize(dto.directorName(), settings.getDirectorName()));
        repository.save(settings);
        return new ServiceMemoSettingsDto(settings.getDirectorTitle(), settings.getDirectorName());
    }

    private ServiceMemoSettings current() {
        return repository.findById(1L).orElseGet(() -> repository.save(new ServiceMemoSettings()));
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }
}
