package org.school.personalLoad.service;

import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.CalendarAudienceDtos;

public interface CalendarAudienceService {
    CalendarAudienceDtos.SettingsView settings(SessionUser user);

    CalendarAudienceDtos.SettingsView update(CalendarAudienceDtos.UpdateRequest request, SessionUser user);
}
