package org.school.personalLoad.service;

import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.ExitOrderDtos;
import org.school.personalLoad.dto.ProbeOrderDtos;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface ExitOrderService {
    ExitOrderDtos.ReferenceData references(String academicYear, SessionUser user);
    List<ExitOrderDtos.OrderView> list(String academicYear, SessionUser user);
    ExitOrderDtos.OrderView create(String academicYear, ExitOrderDtos.CreateRequest request, SessionUser user);
    ExitOrderDtos.OrderView update(Long id, ExitOrderDtos.CreateRequest request, SessionUser user);
    ExitOrderDtos.OrderView acknowledge(Long id, SessionUser user);
    ExitOrderDtos.OrderView generate(Long id, ExitOrderDtos.GenerateRequest request, SessionUser user);
    ProbeOrderDtos.FilePayload generatedDocument(Long id, SessionUser user);
    ExitOrderDtos.OrderView release(Long id, SessionUser user);
    ExitOrderDtos.OrderView uploadScan(Long id, MultipartFile file, SessionUser user) throws IOException;
    ProbeOrderDtos.FilePayload signedScan(Long id, SessionUser user);
    ExitOrderDtos.OrderView markAttendance(Long id, ExitOrderDtos.AttendanceRequest request, SessionUser user);
    ExitOrderDtos.SettingsView settings(String academicYear, SessionUser user);
    ExitOrderDtos.SettingsView updateSettings(String academicYear, ExitOrderDtos.SettingsRequest request, SessionUser user);
    ExitOrderDtos.SummaryView summary(String academicYear, SessionUser user);
    List<ProbeOrderDtos.CalendarEvent> calendar(LocalDate from, LocalDate to);
    List<ProbeOrderDtos.HistoryEvent> studentHistory(Long studentId);
    List<ProbeOrderDtos.HistoryEvent> teacherHistory(Long teacherId);
}
