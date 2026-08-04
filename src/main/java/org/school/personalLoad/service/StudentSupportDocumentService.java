package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.contingent.StudentSupportDocumentDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StudentSupportDocumentService {

    private static final long MAX_ATTACHMENT_SIZE = 15L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "doc", "docx", "xls", "xlsx"
    );

    private final StudentSupportDocumentRepository documentRepository;
    private final StudentSupportDocumentAttachmentRepository attachmentRepository;
    private final StudentProfileRepository studentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public List<StudentSupportDocumentDtos.View> findAll(String academicYear, LocalDate asOfDate) {
        LocalDate effectiveDate = Objects.requireNonNullElse(asOfDate, LocalDate.now());
        return documentRepository
                .findAllByAcademicYearOrderByValidToAscStudent_CurrentFullNameAsc(academicYear).stream()
                .map(document -> toView(document, effectiveDate))
                .toList();
    }

    @Transactional
    public StudentSupportDocumentDtos.View save(String academicYear,
                                                StudentSupportDocumentDtos.SaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Документ не передан");
        }
        if (request.getStudentId() == null) {
            throw new IllegalArgumentException("Выберите ребёнка");
        }
        if (request.getDocumentType() == null) {
            throw new IllegalArgumentException("Выберите тип документа");
        }
        StudentProfile student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        StudentSupportDocument document = request.getId() == null
                ? new StudentSupportDocument()
                : documentRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден"));
        if (document.getId() != null
                && (!Objects.equals(document.getAcademicYear(), academicYear)
                || !Objects.equals(document.getStudent().getId(), student.getId()))) {
            throw new IllegalArgumentException("Документ относится к другому ребёнку или учебному году");
        }
        validateDates(request.getValidFrom(), request.getValidTo());
        document.setStudent(student);
        document.setAcademicYear(academicYear);
        document.setDocumentType(request.getDocumentType());
        document.setAcceptedForm(Objects.requireNonNullElse(
                request.getAcceptedForm(),
                StudentSupportDocumentForm.COPY
        ));
        document.setDocumentNumber(trim(request.getDocumentNumber()));
        document.setIssueDate(request.getIssueDate());
        document.setValidFrom(request.getValidFrom());
        document.setValidTo(request.getValidTo());
        document.setIssuingOrganization(trim(request.getIssuingOrganization()));
        document.setReceivedAt(Objects.requireNonNullElse(request.getReceivedAt(), LocalDate.now()));
        document.setResponsibleEmployee(trim(request.getResponsibleEmployee()));
        document.setComment(trim(request.getComment()));
        document.setUpdatedAt(LocalDateTime.now());
        return toView(documentRepository.save(document), LocalDate.now());
    }

    @Transactional
    public void delete(String academicYear, Long documentId) {
        StudentSupportDocument document = requireDocument(academicYear, documentId);
        attachmentRepository.deleteAllByDocument_Id(document.getId());
        documentRepository.delete(document);
    }

    @Transactional
    public StudentSupportDocumentDtos.AttachmentView addAttachment(String academicYear,
                                                                  Long documentId,
                                                                  MultipartFile file,
                                                                  String username) {
        StudentSupportDocument document = requireDocument(academicYear, documentId);
        validateFile(file);
        try {
            StudentSupportDocumentAttachment attachment = new StudentSupportDocumentAttachment();
            attachment.setDocument(document);
            attachment.setOriginalFileName(cleanFileName(file.getOriginalFilename()));
            attachment.setContentType(Objects.toString(file.getContentType(), "application/octet-stream"));
            attachment.setFileSize(file.getSize());
            attachment.setContent(file.getBytes());
            attachment.setUploadedAt(LocalDateTime.now());
            attachment.setUploadedBy(Objects.toString(username, "SYSTEM"));
            return toAttachmentView(attachmentRepository.save(attachment));
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить прикреплённую копию", exception);
        }
    }

    @Transactional(readOnly = true)
    public StudentSupportDocumentDtos.AttachmentDownload downloadAttachment(String academicYear,
                                                                            Long documentId,
                                                                            Long attachmentId) {
        requireDocument(academicYear, documentId);
        StudentSupportDocumentAttachment attachment = attachmentRepository.findById(attachmentId)
                .filter(item -> Objects.equals(item.getDocument().getId(), documentId))
                .orElseThrow(() -> new IllegalArgumentException("Прикреплённая копия не найдена"));
        return new StudentSupportDocumentDtos.AttachmentDownload(
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getContent()
        );
    }

    @Transactional
    public void deleteAttachment(String academicYear, Long documentId, Long attachmentId) {
        requireDocument(academicYear, documentId);
        StudentSupportDocumentAttachment attachment = attachmentRepository.findById(attachmentId)
                .filter(item -> Objects.equals(item.getDocument().getId(), documentId))
                .orElseThrow(() -> new IllegalArgumentException("Прикреплённая копия не найдена"));
        attachmentRepository.delete(attachment);
    }

    private StudentSupportDocument requireDocument(String academicYear, Long documentId) {
        return documentRepository.findById(documentId)
                .filter(document -> Objects.equals(document.getAcademicYear(), academicYear))
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден"));
    }

    private StudentSupportDocumentDtos.View toView(StudentSupportDocument document, LocalDate asOfDate) {
        StudentSupportDocumentDtos.View view = new StudentSupportDocumentDtos.View();
        view.setId(document.getId());
        view.setStudentId(document.getStudent().getId());
        view.setStudentFullName(document.getStudent().getCurrentFullName());
        view.setClassName(currentClass(document.getStudent().getId(), document.getAcademicYear(), asOfDate));
        view.setDocumentType(document.getDocumentType());
        view.setAcceptedForm(document.getAcceptedForm());
        view.setDocumentNumber(document.getDocumentNumber());
        view.setIssueDate(document.getIssueDate());
        view.setValidFrom(document.getValidFrom());
        view.setValidTo(document.getValidTo());
        view.setIssuingOrganization(document.getIssuingOrganization());
        view.setReceivedAt(document.getReceivedAt());
        view.setResponsibleEmployee(document.getResponsibleEmployee());
        view.setComment(document.getComment());
        view.setValidityStatus(validityStatus(document, asOfDate));
        view.setAttachments(document.getId() == null ? List.of() : attachmentRepository
                .findAllByDocument_IdOrderByUploadedAtAsc(document.getId()).stream()
                .map(this::toAttachmentView)
                .toList());
        return view;
    }

    private StudentSupportDocumentDtos.AttachmentView toAttachmentView(
            StudentSupportDocumentAttachment attachment
    ) {
        StudentSupportDocumentDtos.AttachmentView view = new StudentSupportDocumentDtos.AttachmentView();
        view.setId(attachment.getId());
        view.setFileName(attachment.getOriginalFileName());
        view.setContentType(attachment.getContentType());
        view.setFileSize(attachment.getFileSize());
        view.setUploadedAt(attachment.getUploadedAt());
        view.setUploadedBy(attachment.getUploadedBy());
        return view;
    }

    private String currentClass(Long studentId, String academicYear, LocalDate date) {
        return enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(
                        studentId,
                        academicYear
                ).stream()
                .filter(enrollment -> contains(enrollment.getValidFrom(), enrollment.getValidTo(), date))
                .map(StudentClassEnrollment::getClassName)
                .findFirst()
                .orElse("");
    }

    private String validityStatus(StudentSupportDocument document, LocalDate date) {
        if (document.getValidFrom() != null && date.isBefore(document.getValidFrom())) {
            return "ОЖИДАЕТ НАЧАЛА";
        }
        if (document.getValidTo() == null) {
            return "БЕЗ СРОКА";
        }
        if (date.isAfter(document.getValidTo())) {
            return "ИСТЁК";
        }
        if (!document.getValidTo().isAfter(date.plusDays(30))) {
            return "ИСТЕКАЕТ";
        }
        return "ДЕЙСТВУЕТ";
    }

    private boolean contains(LocalDate from, LocalDate to, LocalDate date) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("Дата окончания не может быть раньше даты начала");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Выберите файл");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new IllegalArgumentException("Размер одного файла не должен превышать 15 МБ");
        }
        String fileName = cleanFileName(file.getOriginalFilename());
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Разрешены PDF, изображения, Word и Excel");
        }
    }

    private String cleanFileName(String value) {
        String fileName = Objects.toString(value, "document").replace('\\', '/');
        int separator = fileName.lastIndexOf('/');
        return (separator >= 0 ? fileName.substring(separator + 1) : fileName)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
    }

    private String trim(String value) {
        String normalized = Objects.toString(value, "").trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
