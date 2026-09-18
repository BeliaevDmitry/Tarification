package org.school.personalLoad.pa.service;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.pa.model.*;
import org.school.personalLoad.pa.repository.PaWorkMaterialFileRepository;
import org.school.personalLoad.pa.repository.PaWorkMaterialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class PaWorkMaterialPersistenceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.school")
    @EnableJpaRepositories("org.school.personalLoad.pa.repository")
    static class JpaTestConfiguration {
    }

    @Autowired
    private PaWorkMaterialRepository materialRepository;
    @Autowired
    private PaWorkMaterialFileRepository fileRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsSeveralAttachmentsForOneWorkAndFindsThemByNaturalKey() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 12, 0);
        PaWorkMaterial material = new PaWorkMaterial();
        material.setAcademicYear("2026/2027");
        material.setSubjectName("Математика");
        material.setScopeType(PaScopeType.CLASS);
        material.setScopeValue("7-А");
        material.setLevel(PaLevel.BASIC);
        material.setWorkType(PaWorkType.EXIT);
        material.setVariantCount(1);
        material.setCreatedAt(now);
        material.setUpdatedAt(now);
        material = materialRepository.saveAndFlush(material);

        fileRepository.saveAndFlush(attachment(material, PaWorkMaterialFileKind.TEXT,
                "вариант-1.pdf", "text-1.pdf", now));
        fileRepository.saveAndFlush(attachment(material, PaWorkMaterialFileKind.TEXT,
                "вариант-2.pdf", "text-2.pdf", now));
        fileRepository.saveAndFlush(attachment(material, PaWorkMaterialFileKind.ANSWERS,
                "ответы.pdf", "answers.pdf", now));
        entityManager.clear();

        assertTrue(materialRepository
                .findFirstByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeOrderByUpdatedAtDesc(
                        "2026/2027", "Математика", PaScopeType.CLASS, "7-А", PaLevel.BASIC, PaWorkType.EXIT)
                .isPresent());
        List<PaWorkMaterialFile> files = fileRepository
                .findAllByMaterialIdOrderByKindAscOriginalFileNameAscIdAsc(material.getId());
        assertEquals(3, files.size());
        assertEquals(2, files.stream().filter(file -> file.getKind() == PaWorkMaterialFileKind.TEXT).count());
        assertTrue(fileRepository
                .findFirstByMaterialIdAndKindAndOriginalFileNameIgnoreCaseOrderByIdDesc(
                        material.getId(), PaWorkMaterialFileKind.TEXT, "ВАРИАНТ-1.PDF")
                .isPresent());
    }

    private PaWorkMaterialFile attachment(PaWorkMaterial material,
                                          PaWorkMaterialFileKind kind,
                                          String originalName,
                                          String storedName,
                                          LocalDateTime uploadedAt) {
        PaWorkMaterialFile file = new PaWorkMaterialFile();
        file.setMaterial(material);
        file.setKind(kind);
        file.setOriginalFileName(originalName);
        file.setStoredFileName(storedName);
        file.setUploadedByUsername("methodist");
        file.setUploadedByFio("Методист");
        file.setUploadedAt(uploadedAt);
        return file;
    }
}
