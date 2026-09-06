package org.school.personalLoad.masterfot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"},showSql=false)
@org.springframework.test.context.ContextConfiguration(classes=FotPersistenceTest.Config.class)
@org.springframework.test.context.ActiveProfiles("master-fot-persistence-test")
class FotPersistenceTest {
    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.Profile("master-fot-persistence-test")
    @org.springframework.boot.autoconfigure.domain.EntityScan(basePackageClasses=FotBatch.class)
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses=FotBatchRepository.class)
    static class Config {}
    @Autowired FotBatchRepository batches;
    @Autowired FotIssueRepository issues;
    @Autowired FotMappingRepository mappings;
    @Autowired TestEntityManager em;
    @Test void persistsIterationDecisionAndMappingAcrossPersistenceContexts() {
        FotBatch batch = new FotBatch(); batch.setAcademicYear("2026/2027"); batch.setFilename("Тест.xlsx");
        batch.setSnapshotDate(FotComparisonTest.DATE); batch.setImportedAt(LocalDateTime.now());
        batch.setSourceJson("{\"rows\":[]}"); batch.setFindingsJson("[]"); batch = batches.saveAndFlush(batch);
        FotIssue issue = new FotIssue(); issue.setId(FotComparison.hash("test")); issue.setAcademicYear("2026/2027");
        issue.setFindingJson("{}"); issue.setFingerprint(FotComparison.hash("values")); issue.setFirstBatchId(batch.getId());
        issue.setLastBatchId(batch.getId()); issue.setStatus("EXPECTED"); issue.setComment("Особенность нагрузки");
        issues.saveAndFlush(issue);
        FotMapping mapping = FotComparisonTest.mapping("SUBJECT","Математика",FotComparison.ABSENT);
        mapping.setId(FotComparison.hash("mapping")); mapping.setAcademicYear("2026/2027"); mappings.saveAndFlush(mapping);
        em.clear();
        assertThat(batches.findAllByAcademicYearOrderByIdDesc("2026/2027")).hasSize(1);
        assertThat(batches.findAllByAcademicYearOrderByIdDesc("2025/2026")).isEmpty();
        FotIssue restored = issues.findAllByAcademicYear("2026/2027").getFirst();
        assertThat(restored.getComment()).isEqualTo("Особенность нагрузки");
        assertThat(restored.getStatus()).isEqualTo("EXPECTED");
        long version = restored.getVersion(); restored.setArchived(true); restored.setArchivedBatchId(batch.getId());
        issues.saveAndFlush(restored); em.clear();
        assertThat(issues.findById(issue.getId()).orElseThrow().getVersion()).isGreaterThan(version);
        assertThat(issues.findById(issue.getId()).orElseThrow().isArchived()).isTrue();
        assertThat(mappings.findAllByAcademicYear("2026/2027")).hasSize(1);
    }
}
