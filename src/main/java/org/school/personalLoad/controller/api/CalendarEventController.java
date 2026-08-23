package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.dto.CalendarDtos;
import org.school.personalLoad.service.CalendarEventService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventService service;

    @GetMapping("/events")
    public List<CalendarDtos.EventView> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        return service.list(from, to, user(request));
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarDtos.EventView create(@RequestBody CalendarDtos.EventRequest body,
                                         HttpServletRequest request) {
        return service.create(body, user(request));
    }

    @PutMapping("/events/{id}")
    public CalendarDtos.EventView update(@PathVariable Long id,
                                         @RequestBody CalendarDtos.EventRequest body,
                                         HttpServletRequest request) {
        return service.update(id, body, user(request));
    }

    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, HttpServletRequest request) {
        service.delete(id, user(request));
    }

    @GetMapping("/bootstrap")
    public CalendarDtos.BootstrapView bootstrap(HttpServletRequest request) {
        return service.bootstrap(user(request));
    }

    @PutMapping("/settings")
    public CalendarDtos.PreferencesView updatePreferences(@RequestBody CalendarDtos.PreferencesRequest body,
                                                           HttpServletRequest request) {
        return service.updatePreferences(body, user(request));
    }

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarDtos.CustomListView createList(@RequestBody CalendarDtos.CustomListRequest body,
                                                   HttpServletRequest request) {
        return service.createCustomList(body, user(request));
    }

    @PutMapping("/lists/{id}")
    public CalendarDtos.CustomListView updateList(@PathVariable Long id,
                                                   @RequestBody CalendarDtos.CustomListRequest body,
                                                   HttpServletRequest request) {
        return service.updateCustomList(id, body, user(request));
    }

    @DeleteMapping("/lists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteList(@PathVariable Long id, HttpServletRequest request) {
        service.deleteCustomList(id, user(request));
    }

    private org.school.personalLoad.auth.SessionUser user(HttpServletRequest request) {
        return AuthSessionUtils.requiredUser(request);
    }
}
