package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.TeacherTimeOffDtos;
import org.school.personalLoad.service.TeacherTimeOffService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/teacher-time-off")
@RequiredArgsConstructor
public class TeacherTimeOffController {
    private final TeacherTimeOffService service;

    @GetMapping("/teachers")
    public List<TeacherTimeOffDtos.TeacherOption> teachers(HttpServletRequest request) {
        requireAccess(request, false);
        return service.teachers();
    }

    @GetMapping
    public TeacherTimeOffDtos.WorkspaceView workspace(HttpServletRequest request) {
        requireAccess(request, false);
        return service.workspace();
    }

    @PostMapping("/accruals")
    public TeacherTimeOffDtos.EntryView addAccrual(@RequestBody TeacherTimeOffDtos.AccrualRequest body,
                                                   HttpServletRequest request) {
        return service.addAccrual(body, requireAccess(request, true));
    }

    @PostMapping("/usages")
    public TeacherTimeOffDtos.EntryView useTimeOff(@RequestBody TeacherTimeOffDtos.UsageRequest body,
                                                   HttpServletRequest request) {
        return service.useTimeOff(body, requireAccess(request, true));
    }

    private SessionUser requireAccess(HttpServletRequest request, boolean edit) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        boolean allowed = edit
                ? user.canEditTab(AppTab.TEACHERS_TIME_OFF)
                : user.canViewTab(AppTab.TEACHERS_TIME_OFF);
        if (!allowed) {
            throw new ForbiddenException(edit
                    ? "Нет прав на внесение данных об отгулах"
                    : "Нет прав на просмотр отгулов");
        }
        return user;
    }
}
