package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.dto.CalendarAudienceDtos;
import org.school.personalLoad.service.CalendarAudienceService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/calendar/audiences")
@RequiredArgsConstructor
public class CalendarAudienceController {

    private final CalendarAudienceService service;

    @GetMapping
    public CalendarAudienceDtos.SettingsView settings(HttpServletRequest request) {
        return service.settings(AuthSessionUtils.requiredUser(request));
    }

    @PutMapping
    public CalendarAudienceDtos.SettingsView update(@RequestBody CalendarAudienceDtos.UpdateRequest body,
                                                    HttpServletRequest request) {
        return service.update(body, AuthSessionUtils.requiredUser(request));
    }
}
