package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.service.ApplicationTimeService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class ApplicationTimeController {
    private final ApplicationTimeService service;

    @GetMapping("/api/application-time")
    public ApplicationTimeService.TimeView current() {
        return service.current();
    }

    @GetMapping("/api/admin/time")
    public ApplicationTimeService.TimeView adminCurrent() {
        return service.current();
    }

    @PutMapping("/api/admin/time")
    public ApplicationTimeService.TimeView update(@RequestBody TimeRequest body, HttpServletRequest request) {
        SessionUser user = (SessionUser) request.getSession().getAttribute(SessionUser.SESSION_KEY);
        String actor = user == null ? "" : user.getFullName();
        return service.setCurrent(body == null ? null : body.currentDateTime(), actor);
    }

    public record TimeRequest(LocalDateTime currentDateTime) {
    }
}
