package org.school.personalLoad.service;

import org.school.personalLoad.dto.MckoDtos;
import org.school.personalLoad.model.MckoCertificate;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface MckoService {
    List<MckoDtos.CertificateRow> certificates(String academicYear, String mode);
    List<MckoDtos.OverviewRow> overview(String academicYear);
    MckoDtos.ImportResult importCertificates(MultipartFile file);
    MckoDtos.CertificateRow createManualCertificate(Long teacherId, String mckoSubject, String examType, LocalDate diagnosticDate,
                                                    String level, boolean published, String comment, MultipartFile scan) throws IOException;
    MckoDtos.CertificateRow updateCertificate(Long id, Long teacherId, String mckoSubject, String examType, LocalDate diagnosticDate,
                                              String level, boolean published, String comment, MultipartFile scan,
                                              boolean removeScan) throws IOException;
    void deleteCertificate(Long id);
    MckoCertificate certificate(Long id);
    List<MckoDtos.SubjectMappingRow> mappings();
    MckoDtos.SubjectMappingRow createMapping(String mckoSubject, Long subjectId, String gradeBand);
    MckoDtos.SubjectMappingRow ignoreSubject(String mckoSubject);
    void deleteMapping(Long id);
    List<MckoDtos.EligibilityRow> eligibility(String academicYear);
    Resource exportCertificates(String academicYear, String mode);
}
