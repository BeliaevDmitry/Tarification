package org.school.personalLoad.service;

import org.school.personalLoad.dto.ServiceMemoSettingsDto;

public interface ServiceMemoSettingsService {
    ServiceMemoSettingsDto get();
    ServiceMemoSettingsDto update(ServiceMemoSettingsDto dto);
}
