package org.school.personalLoad.service;

import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.CalendarDtos;

import java.time.LocalDate;
import java.util.List;

public interface CalendarEventService {
    List<CalendarDtos.EventView> list(LocalDate from, LocalDate to, SessionUser user);

    CalendarDtos.EventView create(CalendarDtos.EventRequest request, SessionUser user);

    CalendarDtos.EventView update(Long id, CalendarDtos.EventRequest request, SessionUser user);

    void delete(Long id, SessionUser user);

    CalendarDtos.BootstrapView bootstrap(SessionUser user);

    CalendarDtos.PreferencesView updatePreferences(CalendarDtos.PreferencesRequest request, SessionUser user);

    CalendarDtos.CustomListView createCustomList(CalendarDtos.CustomListRequest request, SessionUser user);

    CalendarDtos.CustomListView updateCustomList(Long id, CalendarDtos.CustomListRequest request, SessionUser user);

    void deleteCustomList(Long id, SessionUser user);
}
