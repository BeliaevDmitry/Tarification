package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.AcademicLoadOrder;
import org.school.personalLoad.model.AcademicLoadOrderType;
import org.school.personalLoad.repository.AcademicLoadOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
}, showSql = false)
class AcademicLoadOrderPersistenceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.school")
    @EnableJpaRepositories("org.school.personalLoad.repository")
    static class JpaTestConfiguration {
    }

    @Autowired
    private AcademicLoadOrderRepository repository;

    @Test
    void keepsGeneratedDocumentInAcademicYearHistory() {
        AcademicLoadOrder order = new AcademicLoadOrder();
        order.setAcademicYear("2026/2027");
        order.setType(AcademicLoadOrderType.CURRICULUM_APPROVAL);
        order.setOrderNumber("125/1-ОД");
        order.setOrderDate(LocalDate.of(2026, 8, 28));
        order.setSignerName("Иванова Ирина Ивановна");
        order.setSignerPosition("Директор");
        order.setSchoolCodeSnapshot("7");
        order.setSchoolNameSnapshot("ГБОУ Школа № 7");
        order.setSourceItemCount(3);
        order.setCreatedByUsername("director");
        order.setDocumentFilename("order.docx");
        order.setDocumentContent(new byte[]{1, 2, 3, 4});
        repository.saveAndFlush(order);

        var history = repository.findAllByAcademicYearOrderByOrderDateDescCreatedAtDesc("2026/2027");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getDocumentContent()).containsExactly(1, 2, 3, 4);
    }
}
