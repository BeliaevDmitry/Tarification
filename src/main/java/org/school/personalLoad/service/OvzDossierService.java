package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.contingent.OvzDtos;
import org.school.personalLoad.dto.contingent.StudentSupportDocumentDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OvzDossierService {
    private static final String CONSENT_TEMPLATE = "templates/ovz/consent-diagnostics-support-template.docx";
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Map<OvzRoadmapStage, String> STAGE_LABELS = new LinkedHashMap<>();
    static {
        STAGE_LABELS.put(OvzRoadmapStage.CERTIFICATE, "Справка");
        STAGE_LABELS.put(OvzRoadmapStage.APPLICATION, "Заявление");
        STAGE_LABELS.put(OvzRoadmapStage.CONSENT, "Согласие");
        STAGE_LABELS.put(OvzRoadmapStage.PPK_APPOINTMENT, "ППк: назначение");
        STAGE_LABELS.put(OvzRoadmapStage.SPECIALIST_ASSIGNMENT, "Распределение за специалистами");
        STAGE_LABELS.put(OvzRoadmapStage.SPECIAL_CONDITIONS_ORDER, "Приказ о назначении специальных условий");
        STAGE_LABELS.put(OvzRoadmapStage.IOM, "ИОМ");
        STAGE_LABELS.put(OvzRoadmapStage.PPK_IOM, "ППк: ИОМ");
    }

    private final StudentSupportDocumentService documentService;
    private final StudentSupportDocumentRepository documentRepository;
    private final StudentProfileRepository studentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final OvzWorkflowStageRepository stageRepository;
    private final OvzApplicationChoiceRepository choiceRepository;
    private final PpkProtocolRepository ppkRepository;
    private final StudentSupportDocumentCorrectionRepository correctionRepository;
    private final StudentSupportStatusRepository supportStatusRepository;
    private final StudentSupportDocumentAttachmentRepository attachmentRepository;
    private final CorrectionDistributionService distributionService;

    @Transactional(readOnly = true)
    public List<OvzDtos.DossierSummary> registry(String academicYear, LocalDate asOfDate) {
        List<StudentSupportDocumentDtos.View> documents = documentService.findAll(academicYear, asOfDate);
        Map<Long, List<StudentSupportDocumentDtos.View>> byStudent = documents.stream()
                .collect(Collectors.groupingBy(StudentSupportDocumentDtos.View::getStudentId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Map<OvzRoadmapStage, OvzStageStatus>> statuses = stageRepository.findAllByAcademicYear(academicYear).stream()
                .collect(Collectors.groupingBy(s -> s.getStudent().getId(), Collectors.toMap(
                        OvzWorkflowStage::getStage, OvzWorkflowStage::getStatus, (left, right) -> right, () -> new EnumMap<>(OvzRoadmapStage.class))));
        Map<Long, OvzStageStatus> distributionStatuses = distributionService.studentStageStatuses(academicYear);
        return byStudent.entrySet().stream().map(entry -> summary(
                        studentRepository.findById(entry.getKey()).orElseThrow(), academicYear, entry.getValue(),
                        statuses.getOrDefault(entry.getKey(), Map.of()), distributionStatuses.get(entry.getKey())))
                .sorted(Comparator.comparing(OvzDtos.DossierSummary::getClassName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(OvzDtos.DossierSummary::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public OvzDtos.DossierDetail detail(String academicYear, Long studentId) {
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        List<StudentSupportDocumentDtos.View> documents = documentService.findAll(academicYear, LocalDate.now()).stream()
                .filter(d -> Objects.equals(d.getStudentId(), studentId)).toList();
        if (documents.isEmpty()) throw new IllegalArgumentException("В реестре нет справок этого ребёнка");
        synchronizeApplicationChoices(student, academicYear, documents);
        Map<OvzRoadmapStage, OvzStageStatus> statuses = stageRepository.findAllByStudent_IdAndAcademicYear(studentId, academicYear).stream()
                .collect(Collectors.toMap(OvzWorkflowStage::getStage, OvzWorkflowStage::getStatus));
        OvzDtos.DossierSummary base = summary(student, academicYear, documents, statuses,
                distributionService.studentStageStatus(academicYear, studentId));
        OvzDtos.DossierDetail detail = new OvzDtos.DossierDetail();
        copySummary(base, detail);
        detail.setApplicationChoices(choiceRepository.findAllByStudent_IdAndAcademicYearOrderBySpecialistNameAsc(studentId, academicYear)
                .stream().map(this::choiceView).toList());
        detail.setPpkProtocols(ppkRepository.findAllByStudent_IdAndAcademicYearOrderByMeetingDateDesc(studentId, academicYear)
                .stream().map(this::ppkView).toList());
        return detail;
    }

    @Transactional
    public OvzDtos.StageView updateStage(String academicYear, Long studentId, OvzDtos.StageUpdateRequest request) {
        if (request == null || request.getStage() == null || request.getStatus() == null) {
            throw new IllegalArgumentException("Выберите этап и его состояние");
        }
        if (request.getStage() == OvzRoadmapStage.CERTIFICATE
                || request.getStage() == OvzRoadmapStage.SPECIALIST_ASSIGNMENT) {
            throw new IllegalArgumentException("Состояние этого этапа определяется автоматически по данным системы");
        }
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        OvzWorkflowStage stage = stageRepository.findByStudent_IdAndAcademicYearAndStage(studentId, academicYear, request.getStage())
                .orElseGet(OvzWorkflowStage::new);
        stage.setStudent(student); stage.setAcademicYear(academicYear); stage.setStage(request.getStage());
        stage.setStatus(request.getStatus()); stage.setUpdatedAt(LocalDateTime.now());
        stage.setPrintedAt(request.getStatus() == OvzStageStatus.NOT_RELEASED ? null
                : Objects.requireNonNullElse(stage.getPrintedAt(), LocalDateTime.now()));
        stage.setCompletedAt(request.getStatus() == OvzStageStatus.COMPLETED ? LocalDateTime.now() : null);
        stageRepository.save(stage);
        return stageView(request.getStage(), request.getStatus());
    }

    @Transactional
    public List<OvzDtos.ApplicationChoiceView> saveApplicationChoices(
            String academicYear, Long studentId, List<OvzDtos.ApplicationChoiceRequest> requests) {
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        Map<String, OvzApplicationChoice> existing = choiceRepository
                .findAllByStudent_IdAndAcademicYearOrderBySpecialistNameAsc(studentId, academicYear)
                .stream().collect(Collectors.toMap(OvzApplicationChoice::getSpecialistName,
                        Function.identity(), (left, right) -> right, LinkedHashMap::new));
        Map<String, OvzDtos.ApplicationChoiceRequest> requested = new LinkedHashMap<>();
        for (OvzDtos.ApplicationChoiceRequest request : requests == null
                ? List.<OvzDtos.ApplicationChoiceRequest>of() : requests) {
            String specialist = trim(request.getSpecialistName());
            if (specialist != null) requested.put(specialist, request);
        }
        existing.forEach((specialist, choice) -> {
            if (!requested.containsKey(specialist)) choiceRepository.delete(choice);
        });
        requested.forEach((specialist, request) -> {
            OvzApplicationChoice choice = existing.getOrDefault(specialist, new OvzApplicationChoice());
            choice.setStudent(student); choice.setAcademicYear(academicYear); choice.setSpecialistName(specialist);
            choice.setTasks(trim(request.getTasks())); choice.setAgreed(request.isAgreed()); choice.setUpdatedAt(LocalDateTime.now());
            choiceRepository.save(choice);
        });
        distributionService.reconcileStudentAssignments(academicYear, studentId);
        OvzDtos.StageUpdateRequest stage = new OvzDtos.StageUpdateRequest(); stage.setStage(OvzRoadmapStage.APPLICATION);
        stage.setStatus(OvzStageStatus.COMPLETED); updateStage(academicYear, studentId, stage);
        return choiceRepository.findAllByStudent_IdAndAcademicYearOrderBySpecialistNameAsc(studentId, academicYear)
                .stream().map(this::choiceView).toList();
    }

    @Transactional
    public void deleteDossier(String academicYear, Long studentId) {
        List<StudentSupportDocument> documents = documentRepository.findAllByStudent_IdAndAcademicYearOrderByIssueDateDesc(studentId, academicYear);
        for (StudentSupportDocument document : documents) {
            supportStatusRepository.deleteAllBySourceDocumentId(document.getId());
            correctionRepository.deleteAllByDocument_Id(document.getId());
            attachmentRepository.deleteAllByDocument_Id(document.getId());
            documentRepository.delete(document);
        }
        stageRepository.deleteAllByStudent_IdAndAcademicYear(studentId, academicYear);
        choiceRepository.deleteAllByStudent_IdAndAcademicYear(studentId, academicYear);
        distributionService.clearStudentAssignments(academicYear, studentId);
        ppkRepository.findAllByStudent_IdAndAcademicYearOrderByMeetingDateDesc(studentId, academicYear).forEach(protocol -> {
            protocol.setStudent(null); protocol.setClassName(null); protocol.setUpdatedAt(LocalDateTime.now()); ppkRepository.save(protocol);
        });
    }

    @Transactional
    public GeneratedDocument consentTemplate(String academicYear, Long studentId) {
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        try (var input = new ClassPathResource(CONSENT_TEMPLATE).getInputStream()) {
            OvzDtos.StageUpdateRequest update = new OvzDtos.StageUpdateRequest(); update.setStage(OvzRoadmapStage.CONSENT);
            update.setStatus(OvzStageStatus.PRINTED); updateStage(academicYear, studentId, update);
            return new GeneratedDocument("Согласие_на_диагностику_и_сопровождение_СППС.docx", input.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось открыть шаблон согласия", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportRegistry(String academicYear) {
        return exportRegistry(academicYear, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportRegistry(String academicYear, List<Long> orderedStudentIds) {
        List<OvzDtos.DossierSummary> rows = registry(academicYear, LocalDate.now());
        if (orderedStudentIds != null) {
            Map<Long, Integer> order = new LinkedHashMap<>();
            for (Long studentId : orderedStudentIds) {
                if (studentId != null) order.putIfAbsent(studentId, order.size());
            }
            rows = rows.stream().filter(item -> order.containsKey(item.getStudentId()))
                    .sorted(Comparator.comparingInt(item -> order.get(item.getStudentId()))).toList();
        }
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Реестр ОВЗ");
            String[] headers = {"ФК", "ФИО", "Класс", "МСЭ: действие", "Заключение ЦМПК: действие", "Рекомендация ЦМПК", "Нозология", "Коррекционная работа", "Этапы"};
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle(); Font font = workbook.createFont(); font.setBold(true); headerStyle.setFont(font);
            for (int i = 0; i < headers.length; i++) { Cell cell = header.createCell(i); cell.setCellValue(headers[i]); cell.setCellStyle(headerStyle); }
            int index = 1;
            for (OvzDtos.DossierSummary item : rows) {
                Row row = sheet.createRow(index++); row.createCell(0).setCellValue(item.getStudentId()); row.createCell(1).setCellValue(item.getFullName());
                row.createCell(2).setCellValue(item.getClassName());
                row.createCell(3).setCellValue(documentPeriod(item.isMse(), item.getMseValidFrom(), item.getMseValidTo()));
                row.createCell(4).setCellValue(documentPeriod(item.isConclusion(), item.getConclusionValidFrom(), item.getConclusionValidTo()));
                row.createCell(5).setCellValue(yesNo(item.isRecommendation()));
                row.createCell(6).setCellValue(Objects.toString(item.getNosologyCode(), "—"));
                row.createCell(7).setCellValue(item.getCorrectionDirections().stream()
                        .map(c -> c.getSpecialistName() + ": " + Objects.toString(c.getTasks(), "")).collect(Collectors.joining("; ")));
                row.createCell(8).setCellValue(isMseOnly(item) ? "—" : item.getStages().stream()
                        .map(stage -> stage.getLabel() + ": " + stageStatusLabel(stage.getStatus())).collect(Collectors.joining("; ")));
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out); return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Не удалось выгрузить реестр ОВЗ", e); }
    }

    private OvzDtos.DossierSummary summary(StudentProfile student, String academicYear,
                                           List<StudentSupportDocumentDtos.View> docs,
                                           Map<OvzRoadmapStage, OvzStageStatus> statuses,
                                           OvzStageStatus distributionStatus) {
        OvzDtos.DossierSummary result = new OvzDtos.DossierSummary(); result.setStudentId(student.getId());
        result.setFullName(student.getCurrentFullName()); result.setBirthDate(student.getBirthDate()); result.setClassName(currentClass(student.getId(), academicYear));
        StudentSupportDocumentDtos.View mseDocument = document(docs, StudentSupportDocumentType.MSE_CERTIFICATE);
        StudentSupportDocumentDtos.View conclusionDocument = document(docs, StudentSupportDocumentType.CPMPC_CONCLUSION);
        result.setMse(mseDocument != null); result.setMseValidFrom(mseDocument == null ? null : mseDocument.getValidFrom());
        result.setMseValidTo(mseDocument == null ? null : mseDocument.getValidTo());
        result.setConclusion(conclusionDocument != null); result.setConclusionValidFrom(conclusionDocument == null ? null : conclusionDocument.getValidFrom());
        result.setConclusionValidTo(conclusionDocument == null ? null : conclusionDocument.getValidTo());
        result.setRecommendation(has(docs, StudentSupportDocumentType.CPMPC_RECOMMENDATION)); result.setDocuments(docs);
        result.setNosologyCode(docs.stream().filter(document -> document.getDocumentType() == StudentSupportDocumentType.CPMPC_CONCLUSION)
                .map(StudentSupportDocumentDtos.View::getNosologyCode).map(this::trim).filter(Objects::nonNull).findFirst().orElse(null));
        result.setValidTo(docs.stream().map(StudentSupportDocumentDtos.View::getValidTo).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null));
        result.setCorrectionDirections(docs.stream().flatMap(d -> Objects.requireNonNullElse(d.getCorrectionDirections(), List.<StudentSupportDocumentDtos.CorrectionDirectionView>of()).stream())
                .collect(Collectors.toMap(StudentSupportDocumentDtos.CorrectionDirectionView::getSpecialistName, Function.identity(), (a, b) -> b, LinkedHashMap::new)).values().stream().toList());
        result.setStages((isMseOnly(result) ? List.of(OvzRoadmapStage.CERTIFICATE) : STAGE_LABELS.keySet()).stream()
                .map(stage -> stageView(stage, stage == OvzRoadmapStage.CERTIFICATE
                        ? OvzStageStatus.COMPLETED : stage == OvzRoadmapStage.SPECIALIST_ASSIGNMENT
                        ? Objects.requireNonNullElse(distributionStatus, OvzStageStatus.NOT_RELEASED)
                        : statuses.getOrDefault(stage, OvzStageStatus.NOT_RELEASED))).toList());
        return result;
    }

    private void synchronizeApplicationChoices(StudentProfile student, String year, List<StudentSupportDocumentDtos.View> docs) {
        Map<String, StudentSupportDocumentDtos.CorrectionDirectionView> directions = docs.stream()
                .filter(d -> d.getDocumentType() == StudentSupportDocumentType.CPMPC_CONCLUSION
                        || d.getDocumentType() == StudentSupportDocumentType.CPMPC_RECOMMENDATION)
                .flatMap(d -> Objects.requireNonNullElse(d.getCorrectionDirections(), List.<StudentSupportDocumentDtos.CorrectionDirectionView>of()).stream())
                .collect(Collectors.toMap(StudentSupportDocumentDtos.CorrectionDirectionView::getSpecialistName,
                        Function.identity(), (left, right) -> right, LinkedHashMap::new));
        Map<String, OvzApplicationChoice> existing = choiceRepository
                .findAllByStudent_IdAndAcademicYearOrderBySpecialistNameAsc(student.getId(), year)
                .stream().collect(Collectors.toMap(OvzApplicationChoice::getSpecialistName, Function.identity()));
        existing.forEach((name, choice) -> {
            if (!directions.containsKey(name)) choiceRepository.delete(choice);
        });
        directions.values().forEach(direction -> {
                    OvzApplicationChoice current = existing.get(direction.getSpecialistName());
                    if (current != null) {
                        current.setTasks(direction.getTasks());
                        current.setUpdatedAt(LocalDateTime.now());
                        choiceRepository.save(current);
                        return;
                    }
                    OvzApplicationChoice choice = new OvzApplicationChoice(); choice.setStudent(student); choice.setAcademicYear(year);
                    choice.setSpecialistName(direction.getSpecialistName()); choice.setTasks(direction.getTasks()); choice.setAgreed(true);
                    choiceRepository.save(choice);
                });
    }

    private void copySummary(OvzDtos.DossierSummary s, OvzDtos.DossierDetail d) {
        d.setStudentId(s.getStudentId()); d.setFullName(s.getFullName()); d.setBirthDate(s.getBirthDate()); d.setClassName(s.getClassName());
        d.setMse(s.isMse()); d.setMseValidFrom(s.getMseValidFrom()); d.setMseValidTo(s.getMseValidTo());
        d.setConclusion(s.isConclusion()); d.setConclusionValidFrom(s.getConclusionValidFrom()); d.setConclusionValidTo(s.getConclusionValidTo());
        d.setRecommendation(s.isRecommendation()); d.setNosologyCode(s.getNosologyCode()); d.setValidTo(s.getValidTo());
        d.setDocuments(s.getDocuments()); d.setCorrectionDirections(s.getCorrectionDirections()); d.setStages(s.getStages());
    }
    private OvzDtos.StageView stageView(OvzRoadmapStage stage, OvzStageStatus status) { OvzDtos.StageView v = new OvzDtos.StageView(); v.setStage(stage); v.setLabel(STAGE_LABELS.get(stage)); v.setStatus(status); return v; }
    private OvzDtos.ApplicationChoiceView choiceView(OvzApplicationChoice c) { OvzDtos.ApplicationChoiceView v = new OvzDtos.ApplicationChoiceView(); v.setId(c.getId()); v.setSpecialistName(c.getSpecialistName()); v.setTasks(c.getTasks()); v.setAgreed(c.isAgreed()); return v; }
    private OvzDtos.PpkProtocolView ppkView(PpkProtocol p) { OvzDtos.PpkProtocolView v = new OvzDtos.PpkProtocolView(); v.setId(p.getId()); v.setProtocolNumber(p.getProtocolNumber()); v.setMeetingDate(p.getMeetingDate()); v.setProtocolType(p.getProtocolType()); v.setStudentId(p.getStudent() == null ? null : p.getStudent().getId()); v.setStudentFullName(p.getStudent() == null ? null : p.getStudent().getCurrentFullName()); v.setClassName(p.getClassName()); v.setChairName(p.getChairName()); v.setSecretaryName(p.getSecretaryName()); v.setAttendees(p.getAttendees()); v.setInvitedRepresentative(p.getInvitedRepresentative()); v.setAgenda(p.getAgenda()); v.setMeetingNotes(p.getMeetingNotes()); v.setDecisionText(p.getDecisionText()); v.setStatus(p.getStatus()); return v; }
    private boolean has(List<StudentSupportDocumentDtos.View> docs, StudentSupportDocumentType type) { return docs.stream().anyMatch(d -> d.getDocumentType() == type); }
    private StudentSupportDocumentDtos.View document(List<StudentSupportDocumentDtos.View> docs, StudentSupportDocumentType type) { return docs.stream().filter(d -> d.getDocumentType() == type).findFirst().orElse(null); }
    private boolean isMseOnly(OvzDtos.DossierSummary dossier) { return dossier.isMse() && !dossier.isConclusion() && !dossier.isRecommendation(); }
    private String currentClass(Long id, String year) { return enrollmentRepository.findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(id, year).map(StudentClassEnrollment::getClassName).orElseGet(() -> enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(id, year).stream().map(StudentClassEnrollment::getClassName).findFirst().orElse("")); }
    private String trim(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private String yesNo(boolean value) { return value ? "Да" : "Нет"; }
    private String documentPeriod(boolean present, LocalDate validFrom, LocalDate validTo) {
        if (!present) return "—";
        return (validFrom == null ? "не указано" : validFrom.format(DISPLAY_DATE)) + " — "
                + (validTo == null ? "бессрочно" : validTo.format(DISPLAY_DATE));
    }
    private String stageStatusLabel(OvzStageStatus status) {
        if (status == OvzStageStatus.COMPLETED) return "завершён";
        if (status == OvzStageStatus.PRINTED) return "распечатан, не завершён";
        return "не печатали";
    }
    public record GeneratedDocument(String fileName, byte[] content) {}
}
